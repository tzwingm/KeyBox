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

    static final int KEYSB_CR          = 0;		// CRLF is given
    static final int KEYSB_STRGC       = 1;		// ^C
    static final int KEYSB_STRGR       = 2;		// ^C
    static final int KEYSB_BEL         = 3;		// BEL
    static final int KEYSB_BEL_CONTENT = 4;		// Text for last BEL
    static final int KEYSB_DONE        = 5;		// Key is done, no output to database
    static final int KEYSB_INIT_TERM   = 6;    	// First output on terminal screen
    static final int KEYSB_INIT_TERM_P = 7;    	// First output prompt on terminal screen
    static final int KEYSB_UNKNOWN     = -1;	// unknown command or input

	static final int BEL = 7;
	static final int BS  = 8;
	static final int ESC = 27;
	static final String CHAR_BS      				           = Character.toString((char)BS);
	static final String CHAR_BEL      				           = Character.toString((char)BEL);
	static final String CHAR_ESC      				           = Character.toString((char)ESC);
	static final String CURSOR_RIGHT 				           = Character.toString((char)ESC)+"[C";
	static final String CURSOR_BACK_FROM_END                   = CHAR_ESC+"[K";
	static final String CURSOR_DELETE_BACK_FROM_END            = CHAR_BS+CHAR_ESC+"[K";
	static final String CURSOR_DELETE_CHARACTERS_BACK_FROM_POS = CHAR_BS+CHAR_ESC+"[";  // n"P"
	static final String STRING_STRG_R                          = "reverse-i-search";
    
    /**
	 * saves sb data to database
	 * @param con 
	 *  
	 * @param sb			- 	output from host
	 * @param sessionOutput	- 	output for the session
	 * @param inputLine 
	 * 					
	 */
	private static void setAudit(Connection con, StringBuilder sb, SessionOutput sessionOutput, List<StringBuilder> inputLine) {
    	//
    	// Check for end of Input line
		boolean cr     = false; // CRLF
		boolean strgC  = false; // Line break ^C
		boolean bel	   = false;	// BEL 7
		//
		// select command or output data
		int     keySb  = changeInput(inputLine, sb);
		if(0 < inputLine.get(1).length()) {
			bel = ( CHAR_BEL.contains(inputLine.get(1).toString()) );			
		}
		
		switch(keySb) {
		case KEYSB_CR:
		{
			//
			// get the commandline
			String outCommand = inputLine.get(0).toString();
			sessionOutput.setOutput(outCommand);
			// save Command to database
			SessionAuditDB.insertTerminalLog(con, sessionOutput);
			// set Output of new line
			sessionOutput.setOutput("\r\n");
			bel = false;
		}
		case KEYSB_INIT_TERM:
		{
			//
			// check for prompt in key
			String outLine = sb.toString();
			boolean prompt = (outLine.contains(ec2_user) && outLine.contains("$ "));
			if(prompt) {
				outLine    = "";
				int iCount = 0;
				// System.getProperty("line.separator")
				String[] lines = sb.toString().split("\n");
				for(String line: lines) {
					if('\r' == line.charAt(line.length()-1)) {
						line += "\n";
					}
					//						System.out.println("Content = " + line);
					//						System.out.println("Length = " + line.length());
					if(line.contains(ec2_user)) { 
						if(line.contains("$ ")) {
							// Line should now contain only the prompt.
							int iPos = line.indexOf(ec2_user);
							if( -1 != iPos) {
								line = line.substring(iPos);
							}
						} else if((0 == iCount) && line.contains("$") && STRING_STRG_R.contains(inputLine.get(1).toString())) {
							line = "";
						}
					} else {
					}
					outLine += line;
					iCount++;
				}
				sessionOutput.setOutput(outLine);
				SessionAuditDB.insertTerminalLog(con, sessionOutput);
				if(!bel) {
					System.out.println("Clearing inputs for no ctrlR !");
					if(0 < inputLine.size()) {
						inputLine.set(0, new StringBuilder());
					}
				}
			} else {
//				System.out.println("!Promt - Adding input <"+sb.toString()+">");					
//				inputLine.add(sb);
			}
		}
		case KEYSB_STRGC:
		{
			if(!bel) {
				inputLine.set(0, new StringBuilder());
				inputLine.set(1, new StringBuilder());
			}
			break;
		}
		case KEYSB_DONE:
		{
			break;
		}
		case KEYSB_UNKNOWN:
		{
			break;
		}
		default:
		{
			break;
		}
		}
	}

	/**
	 * Gets input strings from terminal
	 * This method should substitute all Cursor codes like BS, ESC[K ... 
	 * 
	 * @param inputLine
	 * @param sb
	 * @return
	 */
	private static int changeInput(List<StringBuilder> inputLine, StringBuilder sb) {
		String input = sb.toString();
		int     keySb  = KEYSB_UNKNOWN;
		
		if( 1 == sb.length()) {
			if(BEL == sb.charAt(0)) {	// Tabulator
				keySb = KEYSB_BEL;
				inputLine.set(1, new StringBuilder(CHAR_BEL));
			}
		} else if( 2 <= sb.length()) {
			if(((sb.charAt(0) == 13) && (sb.charAt(1) == 10))) {	// CRLF
				keySb  = KEYSB_CR;
			} else if( ((sb.charAt(0) == '^') && (sb.charAt(1) == 'C'))) { // StrgC
				keySb  = KEYSB_STRGC;
			} else if(0 == input.indexOf("Last login:")) {			// first message on the terminal
				keySb = KEYSB_INIT_TERM;
				inputLine.set(2, sb);
			} else if(input.contains(STRING_STRG_R)) {				// StrgR reverse seach
				keySb = KEYSB_STRGR;
				inputLine.set(1, new StringBuilder(STRING_STRG_R));
			} else if(input.contains(ec2_user) && input.contains("$ ")) {	// Prompt 
				if(0 == inputLine.get(3).length()) {
					inputLine.set(3, sb);	// set first prompt from start
					keySb = KEYSB_INIT_TERM_P;
				} else if(inputLine.get(1).toString().contains(STRING_STRG_R)) {
					keySb = KEYSB_CR;
				}
			}
		}
		
		if (KEYSB_UNKNOWN == keySb){
			boolean bs	   = false;	// BS  8

			System.out.println("changeInput - getting <"+input+">");
			//
			// At this point we get the optimized Inputs
			//
			if(0 < input.length())	{ // One character
				input = buildCommandLine(inputLine, sb);
			}			
		}

		return keySb;
	}

	/**
	 * Looks for special Key action like
	 * BS, BSESC[K ..
	 * 
	 * @param inputLine	- actual, before next, buffer for command
	 * @param sb		- next data from input
	 * @return
	 */
	private static String buildCommandLine(List<StringBuilder> inputLine, StringBuilder sb) {
		//
		// find command for the result from the host
		String outCommand  = "";
		String LastCommand = "";
		String input       = sb.toString();
		StringBuilder sbLast = new StringBuilder();
		if(0 < inputLine.size()) {
			sbLast = inputLine.get(0);
			if(1 == input.length()) {
				sbLast.append(sb);
			} else if((0 < inputLine.get(1).length()) && (BEL == inputLine.get(1).charAt(0))) {
				
			} else if((0<inputLine.get(1).length()) && (STRING_STRG_R.contains(inputLine.get(1).toString()))) {
				// reverse search is active
				if(input.contains("@ip")) {
					// here we get only a prompt string like "[6@[ec2-user@ip-172-31-3-88 ~]$[C[C"
					// this comes instead of CR !					
				} else {
					// here we get a string like 
					//	"p': pwd" or 
					//  "[C[C[C-tr[K" or
					//  "c': [3Pp': pwd"
					//
					int iPosCursorRight = 0;
					int iCount          = 0;
					while( -1 < (iPosCursorRight=sb.indexOf(CURSOR_RIGHT)) ) {
						sb.delete(iPosCursorRight, iPosCursorRight+CURSOR_RIGHT.length());
						iCount++;
					}
					//
					// Modify last command
					if(0 < iCount) {
						iPosCursorRight = sbLast.indexOf("': ");
						if(-1 < iPosCursorRight) {
							int len = sbLast.length();
							int startDelete = iPosCursorRight+3+iCount;
							if(startDelete < len) {
//								sbLast.delete(startDelete, len);
							}
						}
					}
					//
					// check for delete character  
					//
					//	is on "ESC[K" behind the command
					iPosCursorRight   = sb.indexOf(CURSOR_BACK_FROM_END);
					if(-1 < iPosCursorRight) {
						sb.delete(iPosCursorRight, iPosCursorRight+CURSOR_BACK_FROM_END.length());
					}
					
					// remove "\b"
					sbLast.append(new StringBuilder( replaceSigns(sb.toString(), "\b", 0, 1) ));									
				}
				
			} else {
				int iCount = 0;
				//
				//	is on "ESC[K" before the command
				int iPos   = sb.indexOf(CURSOR_DELETE_BACK_FROM_END);
				if(-1 == iPos) {
					iPos = sb.length();
				}
				for( ; iCount<sb.length(); iCount++) {
					char c = sb.charAt(iCount);
					if(BS == c) {
						if(0 < sbLast.length()) {
							sbLast.deleteCharAt(sbLast.length()-1);
							if(iCount == iPos) {
								iCount += CURSOR_DELETE_BACK_FROM_END.length()-1;
							}
						} else
						{
							break;
						}
					} else {
						break;
					}
				}
				sb.replace(0, iCount, "");
				//
				// Now search for "ESC[xP"
				if(3 < sb.length()) {
					boolean bFoundEsc = false;
					int iPosEsc = sb.indexOf(CHAR_ESC+"[");
					String number = "";
					if(-1 != iPosEsc) {
						for( int i = iPosEsc+2; i<sb.length(); i++) {
							Character c = sb.charAt(i);
							if(!Character.isDigit(c)) {
								if(c == 'P') {
									bFoundEsc = true;
									break;
								}
							} else {
								number += c;
							}
						}
						if(bFoundEsc) {
							long lPosition = Integer.valueOf(number);
							// Delete characters from input string
							sb.delete(iPosEsc, iPosEsc+3+number.length());
						}
					}
				}
				//
				//	is on "ESC[K" behind the command
				iPos   = sb.indexOf(CURSOR_BACK_FROM_END);
				if(-1 < iPos) {
					sb.delete(iPos, iPos+CURSOR_BACK_FROM_END.length());
				}
				
				
				
				sbLast.append(sb);
			}
			inputLine.set(0, sbLast);
			
		} else {
			inputLine.add(sb);
		}
		outCommand = inputLine.get(0).toString();
		System.out.println("buildCommandLine -  <"+outCommand+">");
		return outCommand;
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
		while(-1 < (iPos=s.indexOf(search))) {
			iPosStart = iPos+offStart;
			iPosEnd   = iPos+offEnd;
			String sub = s.substring(iPosStart, iPosEnd);
			s = s.replace(sub, "");
		}
		return s;
	}
}
