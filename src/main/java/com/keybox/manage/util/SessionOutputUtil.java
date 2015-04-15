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
		boolean cr     = false;
		int     keySb  = 0;
		if( 2 <= sb.length()) {
			cr     = ((sb.charAt(0) == 13) && (sb.charAt(1) == 10));
		} else {
			// TAB - 7
			keySb = sb.charAt(0);
		}
		// Check for "reverse-i-search"
		boolean ctrlR  = ((sb.charAt(0) == '\b') && (sb.charAt(1) == '\b') && (sb.charAt(2) == '\b'));

		switch(keySb) {
		case 0:
		{
			if(cr) {
				String outCommand = "";
				for( StringBuilder s : inputLine) {
					String line = s.toString();
					outCommand += line;
				}
				sessionOutput.setOutput(outCommand);
				// save Command to database
				SessionAuditDB.insertTerminalLog(con, sessionOutput);
				inputLine.clear();
			} else if( ctrlR ) {
				// skip here "reverse-i-search" string, saved in inputline[0]

				// take only the content of command - etc. "l : ll" short input, and command
				String outCommand  = sb.toString().substring(3);
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
				inputLine.add(sb);
				//
				// check for prompt in key
				String outLine = sb.toString();
				boolean prompt = (outLine.contains(ec2_user) && outLine.contains("$ "));
				if(prompt) {
					outLine = "";
					String[] lines = sb.toString().split("\n");
					for(String line: lines){
//						System.out.println("Content = " + line);
//						System.out.println("Length = " + line.length());
						if((line.contains(ec2_user) && line.contains("$ "))) {
							// Line should now contain only the prompt.
							int iPos = line.indexOf(ec2_user);
							if( -1 != iPos) {
								line = line.substring(iPos);
							}
						} else {
							line += "\n";
						}
						outLine += line;
					}

					sessionOutput.setOutput(outLine);
					SessionAuditDB.insertTerminalLog(con, sessionOutput);
					inputLine.clear();
				}
			}
			break;
		}
		case 7:
		{
			String outCommand = "";
			for( StringBuilder s : inputLine) {
				if(7 != s.charAt(0)) {
					String line = s.toString();
					outCommand += line;					
				}
			}
			sessionOutput.setOutput(outCommand);
			// save Command to database
			SessionAuditDB.insertTerminalLog(con, sessionOutput);
			inputLine.clear();
			break;
			
		}
		}
	}
}
