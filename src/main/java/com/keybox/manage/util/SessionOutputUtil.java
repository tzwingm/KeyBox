/**
 * Copyright 2013 Sean Kavanagh - sean.p.kavanagh6@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.keybox.manage.util;

import com.keybox.common.util.AppConfig;
import com.keybox.manage.db.SessionAuditDB;
import com.keybox.manage.model.SessionHostOutput;
import com.keybox.manage.model.SessionOutput;
import com.keybox.manage.model.UserSessionsOutput;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility to is used to store the output for a session until the ajax call that brings it to the screen
 */
public class SessionOutputUtil {


    private static Map<Long, UserSessionsOutput> userSessionsOutputMap = new ConcurrentHashMap<Long, UserSessionsOutput>();
    public static boolean enableAudit = "true".equals(AppConfig.getProperty("enableAudit"));
    private static final String ec2_user = "[ec2-user";
    

    /**
     * removes session for user session
     *
     * @param sessionId session id
     */
    public static void removeUserSession(Long sessionId) {
        UserSessionsOutput userSessionsOutput = userSessionsOutputMap.get(sessionId);
        if (userSessionsOutput != null) {
            userSessionsOutput.getSessionOutputMap().clear();
        }
        userSessionsOutputMap.remove(sessionId);

    }

    /**
     * removes session output for host system
     *
     * @param sessionId    session id
     * @param instanceId id of host system instance
     */
    public static void removeOutput(Long sessionId, Integer instanceId) {

        UserSessionsOutput userSessionsOutput = userSessionsOutputMap.get(sessionId);
        if (userSessionsOutput != null) {
            userSessionsOutput.getSessionOutputMap().remove(instanceId);
        }
    }

    /**
     * adds a new output
     *
     * @param sessionId     session id
     * @param hostId        host id
     * @param sessionOutput session output object
     */
    public static void addOutput(Long sessionId, Long hostId, SessionOutput sessionOutput) {

        UserSessionsOutput userSessionsOutput = userSessionsOutputMap.get(sessionId);
        if (userSessionsOutput == null) {
            userSessionsOutputMap.put(sessionId, new UserSessionsOutput());
            userSessionsOutput = userSessionsOutputMap.get(sessionId);
        }
        userSessionsOutput.getSessionOutputMap().put(sessionOutput.getInstanceId(), new SessionHostOutput(hostId, new StringBuilder()));


    }


    /**
     * adds a new output
     *
     * @param sessionId    session id
     * @param instanceId id of host system instance
     * @param value        Array that is the source of characters
     * @param offset       The initial offset
     * @param count        The length
     */
    public static void addToOutput(Long sessionId, Integer instanceId, char value[], int offset, int count) {


        UserSessionsOutput userSessionsOutput = userSessionsOutputMap.get(sessionId);
        if (userSessionsOutput != null) {
            userSessionsOutput.getSessionOutputMap().get(instanceId).getOutput().append(value, offset, count);
        }

    }


    /**
     * returns list of output lines
     *
     * @param sessionId session id object
     * @param inputLine 
     * @return session output list
     */
    public static List<SessionOutput> getOutput(Connection con, Long sessionId, List<StringBuilder> inputLine) {
        List<SessionOutput> outputList = new ArrayList<SessionOutput>();


        UserSessionsOutput userSessionsOutput = userSessionsOutputMap.get(sessionId);
        if (userSessionsOutput != null) {



            for (Integer key : userSessionsOutput.getSessionOutputMap().keySet()) {

                //get output chars and set to output
                try {
                    SessionHostOutput sessionHostOutput = userSessionsOutput.getSessionOutputMap().get(key);
                    Long hostId = sessionHostOutput.getId();
                    StringBuilder sb = sessionHostOutput.getOutput();
                    if (sb != null) {
                        SessionOutput sessionOutput = new SessionOutput();
                        sessionOutput.setSessionId(sessionId);
                        sessionOutput.setHostSystemId(hostId);
                        sessionOutput.setInstanceId(key);

                        sessionOutput.setOutput(sb.toString());

                        if (StringUtils.isNotEmpty(sessionOutput.getOutput())) {
                            outputList.add(sessionOutput);
                                                      
                            if (enableAudit) {
                            	setAudit(con, sb, sessionOutput, inputLine);
                            }
                            userSessionsOutput.getSessionOutputMap().put(key, new SessionHostOutput(hostId, new StringBuilder()));
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }

            }

        }


        return outputList;
    }

	/**
	 * saves sb data to database
	 * @param con 
	 *  
	 * @param sb			- 	output from host
	 * @param sessionOutput	- 	output for the session
	 * @param inputLine 
	 */
	private static void setAudit(Connection con, StringBuilder sb, SessionOutput sessionOutput, List<StringBuilder> inputLine) {
    	//
    	// Check for end of Input line
		final int BEL = 7;
		boolean cr     = false; // CRLF
		boolean bel	   = false;	// BEL 7
		int     keySb  = 0;
		if( 2 <= sb.length()) {
			cr     = ((sb.charAt(0) == 13) && (sb.charAt(1) == 10));
		} else {
			// TAB - 7 ( BEL )
			keySb = sb.charAt(0);
			bel   = (BEL == keySb);
		}
		// Check for "reverse-i-search"
		boolean ctrlR  = false;
		int icount    = 0;
		int iBelIndex = -1;
		for( StringBuilder s : inputLine) {
			String line = s.toString();
			ctrlR = line.contains("reverse-i-search");
			if(((1 == line.length()) && (BEL == line.charAt(0)))) {
				iBelIndex = icount;
				System.out.println("BEL is activated !");
			}
			bel = bel || (iBelIndex == icount);
			if( ctrlR ) {
				System.out.println("reverse-i-search is activated !");
				break;
			}
			icount++;
		}	
		if(0 <= iBelIndex){
			StringBuilder ex = inputLine.remove(iBelIndex);
			ex.toString();
		}
		switch(keySb) {
		case 0:
		{
			if(cr) {
				//
				// find command for the result from the host
				String outCommand = "";
				boolean isSimpleInput       = false;
				boolean isCursorUpDownInput = false;
				String line = "";
				for( StringBuilder s : inputLine) {
					line = s.toString();
					if(1 == line.length()) {
						isSimpleInput = true;
					} else if(line.contains("\b") || (1 < line.length())) {
						isCursorUpDownInput = true;
					}
					outCommand += line;
				}
				if(isCursorUpDownInput && !isSimpleInput) {
					outCommand = removeBacks(line); // take last command
				}
				sessionOutput.setOutput(outCommand);
				// save Command to database
				SessionAuditDB.insertTerminalLog(con, sessionOutput);
				// set Output of new line
				sessionOutput.setOutput("\r\n");
				if(!bel) {
					inputLine.clear();
				}
			} else if( ctrlR ) {
				// skip here "reverse-i-search" string, saved in inputline[0]

				// take only the content of command - etc. "l : ll" short input, and command
				String outCommand  = sb.toString().substring(3);
				
				System.out.println("reverse-i-search string :"+outCommand);
				
				int iPos = outCommand.indexOf('\b');
				if( -1 != iPos ) {
					outCommand = outCommand.substring(0, iPos);
				}
				sessionOutput.setOutput(outCommand);
				// save Command to database
				SessionAuditDB.insertTerminalLog(con, sessionOutput);
				inputLine.clear();
			}
			if( !ctrlR ) {
				//
				// check for prompt in key
				String outLine = sb.toString();
				boolean prompt = (outLine.contains(ec2_user) && outLine.contains("$ "));
				if(prompt) {
					outLine = "";
					// System.getProperty("line.separator")
					String[] lines = sb.toString().split("\n");
					for(String line: lines){
						if('\r' == line.charAt(line.length()-1)) {
							line += "\n";
						}
						//						System.out.println("Content = " + line);
						//						System.out.println("Length = " + line.length());
						if((line.contains(ec2_user) && line.contains("$ "))) {
							// Line should now contain only the prompt.
							int iPos = line.indexOf(ec2_user);
							if( -1 != iPos) {
								line = line.substring(iPos);
							}
						} else {
						}
						outLine += line;
					}

					sessionOutput.setOutput(outLine);
					SessionAuditDB.insertTerminalLog(con, sessionOutput);
					if(!bel) {
						inputLine.clear();
					}
				}
			}
			break;
		}
		case BEL:
		{
			//
			// Save here the BEL information for next call
			inputLine.add(sb);
			break;
			
		}
		default:
		{
			inputLine.add(sb);
		}
		}
	}

	/**
	 * Removes characters from String for each "\b" or others
	 * is used for CURSOR UP/DOWN for commands
	 * 
	 * @param outCommand	- Input string
	 * @return
	 */
	private static String removeBacks(String outCommand) {
		// remove "\b"
		String s = replaceSigns(outCommand, "\b", 0, 1);
		// remove trailing like "ESC[K"
		s = replaceSigns(s, Character.toString((char)27)+"[K", 0, 3);
		s = replaceSigns(s, Character.toString((char)27)+"[?P", 0, 3);
		return s;
	}

	/**
	 * Replace given string s by string search through ""
	 * 
	 * @param s			- string to modify
	 * @param search	- search string
	 * @param offStart	- offset to found position to delete, when found
	 * @param offEnd	- offset to found position to delete, when found
	 * @return modified s
	 */
	private static String replaceSigns(String s, String search, int offStart, int offEnd) {
		int iPos = 0;
		int iPosStart;
		int iPosEnd;
		while(-1 != (iPos=s.indexOf(search))) {
			iPosStart = iPos+offStart;
			iPosEnd   = iPos+offEnd;
			String sub = s.substring(iPosStart, iPosEnd);
			s = s.replace(sub, "");
		}
		return s;
	}
}
