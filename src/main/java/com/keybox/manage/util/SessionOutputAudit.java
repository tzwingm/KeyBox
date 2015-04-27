package com.keybox.manage.util;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import org.apache.struts2.views.xslt.ArrayAdapter;

import com.keybox.manage.db.SessionAuditDB;
import com.keybox.manage.model.SessionOutput;

public class SessionOutputAudit {

    private static final int KEYSB_CR          = 0;		// CRLF is given
    private static final int KEYSB_STRGC       = 1;		// ^C
    private static final int KEYSB_STRGR       = 2;		// ^C
    private static final int KEYSB_BEL         = 3;		// BEL
    private static final int KEYSB_BEL_CONTENT = 4;		// Text for last BEL
    private static final int KEYSB_DONE        = 5;		// Key is done, no output to database
    private static final int KEYSB_INIT_TERM   = 6;    	// First output on terminal screen
    private static final int KEYSB_INIT_TERM_P = 7;    	// First output prompt on terminal screen
    private static final int KEYSB_UNKNOWN     = -1;	// unknown command or input

	private static final int BEL = 7;
	private static final int BS  = 8;
	private static final int ESC = 27;
	private static final String CHAR_BS      				           = Character.toString((char)BS);
	private static final String CHAR_BEL      				           = Character.toString((char)BEL);
	private static final String CHAR_ESC      				           = Character.toString((char)ESC);
	private static final String CURSOR_RIGHT 				           = Character.toString((char)ESC)+"[C";
	private static final String CURSOR_BACK_FROM_END                   = CHAR_ESC+"[K";
	private static final String CURSOR_DELETE_BACK_FROM_END            = CHAR_BS+CHAR_ESC+"[K";
	private static final String CURSOR_DELETE_CHARACTERS_BACK_FROM_POS = CHAR_BS+CHAR_ESC+"[";  // n"P"
	private static final String STRING_STRG_R                          = "reverse-i-search";
	private static final String STRING_STRG_R_INSERT_KEY               = CHAR_ESC+"[1@";		// next letter is that for insert
	private static final String STRING_STRG_R_INSERT_MASK              = "': ";		// next letter is that for insert
    private static final String STRING_EC2_USER = "[ec2-user";
    private static final String STRING_DOLLAR_BLANC = "$ ";

    private static boolean activeBell  = false;
    private static boolean checkBelCommand = false;
    private static StringBuilder  belCommandBehindDollar = new StringBuilder();
    private static boolean activeStrgR = false;
    private static StringBuilder outCommand   		= new StringBuilder();
    private static StringBuilder outputFromCommand  = new StringBuilder();

    private static boolean activePrompt = false;

    private static int iPosUser   = 0;
    private static int iPosDollar = 0;
    /**
	 * saves sb data to database
	 * @param con
	 *
	 * @param sb			- 	output from host
	 * @param sessionOutput	- 	output for the session
	 * @param inputLine
	 *
	 */
	void setAudit(Connection con, StringBuilder sb, SessionOutput sessionOutput, List<StringBuilder> inputLine) {
		//
		// select command or output data
		boolean    checkBelCommand 		= false;	// If there is something behind "$ " for example "$ pw", then keep old command !
		List<StringBuilder> arrSbLines 	= new ArrayList<>();
		activePrompt = false;					// start with no active prompt

		switch(changeInput(inputLine, sb, arrSbLines)) {
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
			//
			//
			if(activeBell) {
				activeBell = false;
				checkBelCommand = true;

			}
//			bel = false;
		}
		case KEYSB_INIT_TERM:
		{
			//
			// check for prompt in key

			sessionOutput.setOutput(outputFromCommand.toString());
			SessionAuditDB.insertTerminalLog(con, sessionOutput);

		}
		case KEYSB_STRGC:
		{
			if(!activeBell) {
				inputLine.set(1, new StringBuilder());
				if( !checkBelCommand) {
					this.outCommand = new StringBuilder();
					inputLine.set(0, new StringBuilder());
				}
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
		case KEYSB_BEL:
		{
			activeBell = true;
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
	 * @param inputLine	- actual, before next, buffer for command
	 * @param sb		- next data from input
	 * @param arrSbLines
	 * @return
	 */
	private static int changeInput(List<StringBuilder> inputLine, StringBuilder sb, List<StringBuilder> arrSbLines) {
		String input = sb.toString();
		System.out.println("changeInput - getting <"+input+">");
		int     keySb  = KEYSB_UNKNOWN;
		boolean isCommand = true;

		switch (sb.length()) {
		case 0:
		{
			break;
		}
		case 1:	// input on character
		{
			if(BEL == sb.charAt(0)) {	// Tabulator
				keySb = KEYSB_BEL;
				activeBell = true;
//				inputLine.set(1, new StringBuilder(CHAR_BEL));
			} else {
				buildCommandLine(inputLine, sb);
			}
			break;
		}
		case 2:
		{
			break;
		}
		default:
		{
			boolean prompt = checkPrompt(sb);
			//
			// check for an output or command
			String[] lines = sb.toString().split("\n");		// separate to lines
			for(String line: lines) {
				if('\r' == line.charAt(line.length()-1)) {
					line += "\n";
				}
				arrSbLines.add(new StringBuilder(line));
			}
			switch ( arrSbLines.size() ) {
			case 0:
			{
				break;
			}
			case 1:
			{
				if((-1<sb.indexOf(STRING_STRG_R))) {				// StrgR reverse search
					keySb = KEYSB_STRGR;
					inputLine.set(1, new StringBuilder(STRING_STRG_R));
					activeStrgR = true;
				} else if(prompt) {
					if(0 == inputLine.get(3).length()) {
						inputLine.set(3, sb);	// set first prompt from start
						keySb = KEYSB_INIT_TERM_P;
					}
				} else if(0 < input.length())	{ // One character
					//
					// At this point we get the optimized Inputs
					//
					buildCommandLine(inputLine, sb);
				}

				// buildCommandLine ???
				break;
			}
			default:
			{
				if(0 == sb.indexOf("Last login:")) {			// first message on the terminal
					keySb = KEYSB_INIT_TERM;
					inputLine.set(2, sb);
					isCommand = false;
				} else if(activeBell) {
					outputFromCommand = checkOutFromCommand(arrSbLines);
					keySb = KEYSB_CR;
				} else if( activeStrgR) {

				}
				break;
			}
			}
			break;
		}
		}
		if( 1 == sb.length()) {
		} else if( 2 <= sb.length()) {
			if(((sb.charAt(0) == 13) && (sb.charAt(1) == 10))) {	// CRLF
				keySb  = KEYSB_CR;
			} else if( ((sb.charAt(0) == '^') && (sb.charAt(1) == 'C'))) { // StrgC
				keySb  = KEYSB_STRGC;
			}
			if(!isCommand && checkPrompt(sb) && (KEYSB_INIT_TERM != keySb)) {
				System.out.println("changeInput - found prompt inputLine.get(1) , BEL, StrgR <"+inputLine.get(1).toString()+","+Boolean.valueOf(activeBell).toString()+","+Boolean.valueOf(activeStrgR).toString()+">");
				if(activeStrgR || inputLine.get(1).toString().contains(STRING_STRG_R)) {
					keySb = KEYSB_CR;
				}
			}
		}

		if (KEYSB_UNKNOWN == keySb){
			/*
			// At this point we get the optimized Inputs
			//
			if(0 < input.length())	{ // One character
				buildCommandLine(inputLine, sb);
			}
			*/
		}

		return keySb;
	}

	/**
	 * Looks into each line of the output to terminal
	 * @param arrSbLines	- all line for output
	 * @return				- one output string like sb, but modified
	 */
	private static StringBuilder checkOutFromCommand(List<StringBuilder> arrSbLines) {
		String	outLine    = "";
		int iCount = 0;
		// System.getProperty("line.separator")

		for(StringBuilder sbLine: arrSbLines) {
			String line = sbLine.toString();
			StringBuilder sbCommand 	 = new StringBuilder();
			StringBuilder modfiedCommand = new StringBuilder();
			if( checkPrompt(line, iCount, modfiedCommand, sbCommand)) {
				if(0 < sbCommand.length()) {
					belCommandBehindDollar = sbCommand;
				} else {
					checkBelCommand = false;	// end of TAB / BEL
				}

			}
			outLine += modfiedCommand.toString();
			iCount++;
		}

		return new StringBuilder(outLine);
	}

	/**
	 * check for prompt in line
	 *
	 * @param line		- one line of the output
	 * @param sbCommand	- if there is an command at the end of the prompt, this is filled within the content
	 * @return
	 */
	private static boolean checkPrompt(String line, long iCount, StringBuilder modifiedLine, StringBuilder sbCommand) {
		StringBuilder sbLine = new StringBuilder(line);
		boolean       bBack = checkPrompt(sbLine);
		if(bBack) {
			//						System.out.println("Content = " + line);
			//						System.out.println("Length = " + line.length());
			if(-1 < iPosUser) {
				int iPosDollar = line.indexOf(STRING_DOLLAR_BLANC);
				if(-1 < iPosDollar) {
					// Line should now contain only the prompt.
					int iPosCommand = iPosDollar+STRING_DOLLAR_BLANC.length();
					if(iPosCommand < line.length()) {
						// there is something behind "$ "
						sbCommand.append(sbLine.substring(iPosCommand, sbLine.length()));
					}
				}
				if( -1 != iPosUser) {
					line = line.substring(iPosUser);
				}
				//			} else if((0 == iCount) && line.contains("$") && STRING_STRG_R.contains(inputLine.get(1).toString())) {
			} else if((0 == iCount) && line.contains("$") && activeStrgR) {
				line = "";
			}
		} else {
		}
		modifiedLine.append(line);
		return false;
	}

	/**
	 * checks content sb for lines like:
	 * "]0;ec2-user@ip-172-31-3-88:~[ec2-user@ip-172-31-3-88 ~]$ " or
	 * "]0;ec2-user@ip-172-31-3-88:~[ec2-user@ip-172-31-3-88 ~]$ pw"
	 *
	 * @param sb		- next data from input
	 * @return	true, if upper lines are matching
	 */
	private static boolean checkPrompt(StringBuilder sb) {
		boolean bRet = false;
		iPosUser   = sb.indexOf(STRING_EC2_USER);
		if(-1 < iPosUser ) {
			iPosDollar = sb.indexOf("$ ");
			if(.1 < iPosDollar) {
				bRet = true;
			}
		}
		return bRet;
	}

	/**
	 * Set the given cursor position to inputline
	 * @param inputLine	- actual, before next, buffer for command
	 * @param cursorPosition
	 */
	private static Integer getCursorPosition(List<StringBuilder> inputLine) {
		String s = inputLine.get(4).toString();
		if(s.isEmpty()) {
			s="0";
		}
		return Integer.valueOf(s);
	}

	/**
	 * Set the given cursor position to inputline
	 * @param inputLine	- actual, before next, buffer for command
	 * @param cursorPosition
	 */
	private static void setCursorPosition(List<StringBuilder> inputLine, Integer cursorPosition) {
		String cursor = cursorPosition.toString();
		inputLine.set(4, new StringBuilder(cursor));
	}

	/**
	 * Looks for special Key action like
	 * BS, BSESC[K ..
	 *
	 * @param inputLine	- actual, before next, buffer for command
	 * @param sb		- next data from input
	 * @return
	 */
	private static void buildCommandLine(List<StringBuilder> inputLine, StringBuilder sb) {
		//
		// find command for the result from the host
		Integer cursorPosition = getCursorPosition(inputLine);
		String input       = sb.toString();
		StringBuilder sbLast = new StringBuilder();
		if(0 < inputLine.size()) {
			sbLast = inputLine.get(0);
			System.out.println("buildCommandLine - buildCommandLine sbLast start <"+sbLast.toString()+">");
			if(1 == input.length()) {
				sbLast.append(sb);
				setCursorPosition(inputLine, ++cursorPosition);
			} else if((0 < inputLine.get(1).length()) && (BEL == inputLine.get(1).charAt(0))) {
				System.out.println("buildCommandLine - TAB / BEL is active !");
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

					int iPosCursorRight 	= 0;
					int cursorRight         = getCursorRght(sb);
					int lettersToDelete   	= getLettersToDelete(sb);
					int cursorBackFromEnd 	= getCursorBackFromEnd(sb);
					int bsCount             = getBs(sb);
					Character charInsert    = getStrgRKey(sb);
					//
					// Modify last command
					//
					int startPositionSb   	= sb.indexOf(STRING_STRG_R_INSERT_MASK);
					int startPosition   	= sbLast.indexOf(STRING_STRG_R_INSERT_MASK);
					if((-1 < startPositionSb) || (0 < charInsert)) {	// found new key Value
						if(-1 < startPosition) {
							if(0 == charInsert) {
								charInsert = sb.charAt(0);
								sb.delete(0, startPosition+3);
							}
							sbLast.insert(startPosition, charInsert);
							startPosition++;
						}
					}
					if(-1 < startPosition) {
						startPosition += STRING_STRG_R_INSERT_MASK.length();	// "': "
						startPosition += cursorRight;	// [C
						if(0 < lettersToDelete) {
							int endPosition = startPosition+lettersToDelete;
							if(startPosition > sbLast.length()) {
								startPosition = sbLast.length();
							}
							if(endPosition > sbLast.length()) {
								endPosition = sbLast.length();
							}
							// delete character from ESC[xP
							sbLast.delete(startPosition, endPosition);
							endPosition = startPosition+sb.length();
							if(endPosition > sbLast.length()) {
								endPosition = sbLast.length();
							}
							sbLast.replace(startPosition, endPosition, sb.toString());
						} else {
							// delete all behind startPosition
							sbLast.delete(startPosition, sbLast.length());
							sbLast.append(sb);
						}
					} else {
						sbLast.append(sb);
					}
					/*
					// check for delete character
					//
					//	is on "ESC[K" behind the command
					iPosCursorRight   = sb.indexOf(CURSOR_BACK_FROM_END);
					if(-1 < iPosCursorRight) {
						sb.delete(iPosCursorRight, iPosCursorRight+CURSOR_BACK_FROM_END.length());
					}

					// remove "\b"
					sbLast.append(new StringBuilder( replaceSigns(sb.toString(), "\b", 0, 1) ));
					*/
				}

			} else {
				int cursorRight = 0;
				//
				//	is on "ESC[K" before the command
				int iPos   = sb.indexOf(CURSOR_DELETE_BACK_FROM_END);
				if(-1 == iPos) {
					iPos = sb.length();
				}
				for( ; cursorRight<sb.length(); cursorRight++) {
					char c = sb.charAt(cursorRight);
					if(BS == c) {
						if(0 < sbLast.length()) {
							sbLast.deleteCharAt(sbLast.length()-1);
							if(cursorRight == iPos) {
								cursorRight += CURSOR_DELETE_BACK_FROM_END.length()-1;
							}
						} else
						{
							break;
						}
					} else {
						break;
					}
				}
				sb.replace(0, cursorRight, "");

				//
				// Now search for "ESC[xP"
				int lettersToDelete = getLettersToDelete(sb);
				//
				//	is on "ESC[K" behind the command
				iPos   = sb.indexOf(CURSOR_BACK_FROM_END);
				if(-1 < iPos) {
					sb.delete(iPos, iPos+CURSOR_BACK_FROM_END.length());
				}
				sbLast.append(sb);
			}
//			this.outCommand = sbLast;
			inputLine.set(0, sbLast);

		} else {
			inputLine.add(sb);
		}
		System.out.println("buildCommandLine - buildCommandLine sbLast, sb end <"+sbLast.toString()+"-- , --"+sb.toString()+">");
		return;
	}

	/**
	 * Checks for something like "p': pwd[1@x"
	 * While StrgR is active comes this or  "x': .."
	 *
	 * @param sb	- input from Terminal
	 * @return	Character from Input
	 */
	private static Character getStrgRKey(StringBuilder sb) {
		Character cKey = new Character((char) 0);
		//
		//	is on "ESC[K" behind the command
		int iPos   = sb.indexOf(STRING_STRG_R_INSERT_KEY);
		if(-1 < iPos) {
			sb.delete(iPos, iPos+STRING_STRG_R_INSERT_KEY.length());
			cKey  = sb.charAt(iPos);
			sb.delete(iPos, iPos+1);
		}
		return cKey;
	}

	/**
	 * Check for BS and delete them in sb
	 * @param sb 	- input from Terminal
	 * @return count of BS
	 */
	private static int getBs(StringBuilder sb) {
		int iCount = 0;
		int iPosBs = 0;
		while( -1 < (iPosBs=sb.indexOf(CHAR_BS)) ) {
			sb.delete(iPosBs, iPosBs+CHAR_BS.length());
			iCount++;
		}
		return iCount;
	}

	/**
	 * Checks for "[K" and delete it in sb
	 * @param sb 	- input from Terminal
	 * @return
	 */
	private static int getCursorBackFromEnd(StringBuilder sb) {
		//
		//	is on "ESC[K" behind the command
		int iPos   = sb.indexOf(CURSOR_BACK_FROM_END);
		if(-1 < iPos) {
			sb.delete(iPos, iPos+CURSOR_BACK_FROM_END.length());
		}
		return iPos;
	}

	/**
	 * Check for "ESC[xP", delete it in sb
	 * @param 	- input from Terminal
	 * @return	x
	 */
	private static int getLettersToDelete(StringBuilder sb) {
		String number = "";
		int lettersToDelete = 0;
		//
		// Now search for "ESC[xP"
		if(3 < sb.length()) {
			boolean bFoundEsc = false;
			int iPosEsc = sb.indexOf(CHAR_ESC+"[");
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
					// Delete characters from input string
					sb.delete(iPosEsc, iPosEsc+3+number.length());
					lettersToDelete = Integer.valueOf(number);
				}
			}
		}
		return lettersToDelete;
	}

	/**
	 * Checks for "[C", count them and delete it in sb
	 * @param sb	- input from Terminal
	 * @return	number of found occurrence
	 */
	private static int getCursorRght(StringBuilder sb) {
		int iCount = 0;
		int iPosCursorRight = 0;
		while( -1 < (iPosCursorRight=sb.indexOf(CURSOR_RIGHT)) ) {
			sb.delete(iPosCursorRight, iPosCursorRight+CURSOR_RIGHT.length());
			iCount++;
		}
		return iCount;
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
//		s = replaceSigns(s, Character.toString((char)27)+"[?P", 0, 3);
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
