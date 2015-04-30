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
	private static final String CURSOR_DELETE_FROM_POSITION_TO_END     = CHAR_ESC+"[K";
	private static final String CURSOR_DELETE_CHARACTERS_BACK_FROM_POS = CHAR_ESC+"[";  // n"P"
	private static final String STRING_STRG_R                          = "reverse-i-search";
    private static final String STRING_STRG_R_PROMT_PART			   = CHAR_BS+CHAR_BS+CHAR_BS+CHAR_ESC+"["; // 23@ .. "': "
	private static final String STRING_STRG_R_INSERT_KEY               = CHAR_ESC+"[1@";		// next letter is that for insert
	private static final String STRING_STRG_R_INSERT_MASK              = "': ";		// next letter is that for insert
    private static final String STRING_EC2_USER 					   = "[ec2-user";
    private static final String STRING_DOLLAR_BLANC 				   = "$ ";

    private static boolean activeBell  = false;
    private static boolean checkBelCommand = false;
    private static StringBuilder  belCommandBehindDollar = new StringBuilder();
    private static boolean activeStrgR = false;
    private 	   StringBuilder outCommand   		= new StringBuilder();
    private static StringBuilder outputFromCommand  = new StringBuilder();

    private static boolean activePrompt = false;

    private static int iPosUser   = 0;
    private static int iPosDollar = 0;

    private long cursorPosition = 0;

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
		checkBelCommand 				= false;			// If there is something behind "$ " for example "$ pw", then keep old command !
		List<StringBuilder> arrSbLines 	= new ArrayList<>();
		activePrompt 					= false;			// start with no active prompt

		switch(changeInput(inputLine, sb, arrSbLines)) {
		case KEYSB_CR:
		{
			//
			// get the commandline
			sessionOutput.setOutput(inputLine.get(0).toString());
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
		}
		case KEYSB_INIT_TERM:
		{
			//
			// check for prompt in key

			sessionOutput.setOutput(outputFromCommand.toString());
			SessionAuditDB.insertTerminalLog(con, sessionOutput);
			outputFromCommand.delete(0, outputFromCommand.length());
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
	private int changeInput(List<StringBuilder> inputLine, StringBuilder sb, List<StringBuilder> arrSbLines) {
		String input = sb.toString();
		System.out.println("changeInput - getting <"+input+">");
		int     keySb  = KEYSB_UNKNOWN;
		boolean isCommand = true;

		//
		// First check length of input
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
			} else {
				buildCommandLine(inputLine, sb);	// key from input
			}
			break;
		}
		default:	// All other
		{
			activePrompt = checkPrompt(sb);
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
			case 1:			// one input line for working in the terminal line
			{
				if((-1<sb.indexOf(STRING_STRG_R))) {				// StrgR reverse search
					keySb = KEYSB_STRGR;
					inputLine.set(1, new StringBuilder(STRING_STRG_R));
					activeStrgR = true;
				} else if(activePrompt) {
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
					outputFromCommand = checkOutFromCommand(arrSbLines);
					isCommand = false;
				} else if(activeBell) {
					outputFromCommand = checkOutFromCommand(arrSbLines);
					inputLine.set(0, belCommandBehindDollar);
					keySb = KEYSB_CR;
				} else if( activeStrgR) {
					outputFromCommand = checkOutFromCommand(arrSbLines);
					keySb = KEYSB_CR;
				} else if(((sb.charAt(0) == 13) && (sb.charAt(1) == 10))) {	// CRLF
					outputFromCommand = checkOutFromCommand(arrSbLines);
					keySb  = KEYSB_CR;
				} else if( ((sb.charAt(0) == '^') && (sb.charAt(1) == 'C'))) { // StrgC
					outputFromCommand = checkOutFromCommand(arrSbLines);
					keySb  = KEYSB_STRGC;
				}
				break;
			}
			}
			break;
		}
		}
		if( 1 == sb.length()) {
		} else if( 2 <= sb.length()) {

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
			} else if((0 == iCount) && line.contains("$") && activeStrgR) {
				line = "";
			}
		} else {
		}
		modifiedLine.append(line);
		return bBack;
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
	private  long getCursorPosition(List<StringBuilder> inputLine) {
		if(cursorPosition > inputLine.get(0).length()) {
			cursorPosition = inputLine.get(0).length();
		}
		return cursorPosition;
	}

	/**
	 * Set the given cursor position to inputline
	 * @param sbLast	- actual, before next, buffer for command
	 * @param cursorPosition
	 */
	private void setCursorPosition(StringBuilder sbLast, long cursorPosition) {
		if(cursorPosition > sbLast.length()) {
			this.cursorPosition = sbLast.length();
		} else if(0 > cursorPosition) {
			this.cursorPosition = 0;
		} else {
			this.cursorPosition = cursorPosition;
		}
	}

	/**
	 * Looks for special Key action like
	 * BS, BSESC[K ..
	 *
	 * @param inputLine	- actual, before next, buffer for command
	 * @param sb		- next data from input
	 * @return
	 */
	private void buildCommandLine(List<StringBuilder> inputLine, StringBuilder sb) {
		//
		// find command for the result from the host
		String input       = sb.toString();
		StringBuilder sbLast = new StringBuilder(inputLine.get(0));
		if(0 < inputLine.size()) {
			int iCursurRight 	= getCursorRght(sb);
			if( 0 < iCursurRight){
				setCursorPosition(sbLast, cursorPosition+iCursurRight);
			}
			int lettersToDelete 			= getDeleteCharactersBackFromPos(sb);
			int cursorCountOfBSbeforeText 	= getCountOfBSbeforeText(sb);
			int cursorDeleteFromPositioToEnd= getCursorDeleteFromPositionToEnd(sb);	// ClenUp ESC{K
			System.out.println("buildCommandLine - buildCommandLine sbLast start <"+sbLast.toString()+">");
			if(1 == input.length()) {
				if(CHAR_BS.contains(sb)) {
					cursorPosition--;
				} else {
					sbLast.append(sb);
					cursorPosition++;
				}
				setCursorPosition(sbLast, cursorPosition);
			} else if(activeBell) {
				System.out.println("buildCommandLine - TAB / BEL is active !");
			} else if(activeStrgR) {
				checkStrgR(sb, sbLast, iCursurRight, lettersToDelete, cursorDeleteFromPositioToEnd);
				activeStrgR = false;
			} else if( checkInsertKey(sb, sbLast, lettersToDelete)){

			} else if (checkOverWriteCommand(sb, sbLast, cursorCountOfBSbeforeText, lettersToDelete, cursorDeleteFromPositioToEnd)) {

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
	 * Count the number of BS from start of the string
	 *
	 * @param sb	- next data from input
	 * @return number of BS
	 */
	private int getCountOfBSbeforeText(StringBuilder sb) {
		int iCount = 0;
		for(; iCount < sb.length(); iCount++) {
			if(BS != sb.charAt(iCount) ) {
				break;
			}
		}
		return iCount;
	}

	/**
	 * checks for starting BS and lettersToDelete
	 * rubs out old command with BS and replaces the rest with the new command part
	 * inputs like:
	 * "[7P| grep "txt""
	 * "pwdx[K"
	 *
	 * @param sb							- next data from input
	 * @param sbLast						- Last Command, "ll /etc/init.d/single "
	 * @param cursorCountOfBSbeforeText		- Number of BS before starting text
	 * @param lettersToDelete				- from "[7P"
	 * @param cursorDeleteFromPositioToEnd	- count of BSESC[K
	 * @return
	 */
	private boolean checkOverWriteCommand(StringBuilder sb,	StringBuilder sbLast, int cursorCountOfBSbeforeText, int lettersToDelete, int cursorDeleteFromPositioToEnd) {
		boolean bBack 		= false;
		int cursorPosition 	= sbLast.length()-cursorCountOfBSbeforeText;
		int cursorDelBack 	= 0;
		if( (0<cursorCountOfBSbeforeText) || (0<lettersToDelete || (-1 < cursorDeleteFromPositioToEnd))) {
			int iEnd = 0;
			//
			// the input means:
			// go back from the end of <sbLast> for <cursorCountOfBSbeforeText>
			// than skip number of <lettersToDelete>
			// than replace sbLast with the input string <sb>
			getBs(sb);	// clean all BS in input
			if(0 == cursorPosition) {
				if(-1 < cursorDeleteFromPositioToEnd) {
					sbLast.delete(cursorPosition, sbLast.length());
				} else if(0<lettersToDelete) {
					iEnd = lettersToDelete+sb.length();
					sbLast.delete(cursorPosition, iEnd);
				} else if(sbLast.length() <= sb.length()) {
					sbLast.delete(0, sbLast.length());
				}
				sbLast.append(sb);
			} else if(0 < cursorPosition) {
				if(0 < lettersToDelete) {
					int iBeg = cursorPosition-lettersToDelete;	// Go back from position
					if(0 > iBeg) {
						iBeg = 0;
					}
					sbLast.delete(iBeg, cursorPosition);
				}
				iEnd = cursorPosition+lettersToDelete;
				if(iEnd < sbLast.length()) {
					sbLast.delete(cursorPosition, iEnd);
				}
				iEnd = cursorPosition+sb.length();
				sbLast.replace(cursorPosition, iEnd, sb.toString());
				bBack = true;
			}
		} else {
			sbLast.append(sb);
		}

		if(false) {
		/*
		// First delete the BSESC[K letters
		for( ;  cursorBackFromEnd > 0; cursorBackFromEnd--) {
			sbLast.deleteCharAt(sbLast.length()-1);
		} */
		//
		//	is on "ESC[K" before the command
		System.out.println("checkOverWriteCommand - cursorBackFromEnd = <"+Integer.valueOf(cursorDeleteFromPositioToEnd).toString()+">");
		for( ; cursorDelBack<sb.length(); cursorDelBack++) {
			char c = sb.charAt(cursorDelBack);
			if(BS == c) {
				if(0 < sbLast.length()) {
					sbLast.deleteCharAt(sbLast.length()-1);
					bBack = true;
				} else
				{
					break;
				}
			} else {
				break;
			}
		}
		sb.replace(0, cursorDelBack, "");

		//
		// Now do something for "ESC[xP" ( x is lettersToDelete
		if( sbLast.length() < lettersToDelete) {
			lettersToDelete = sbLast.length();
		}
		sbLast.replace(0, lettersToDelete, "");
		//
		//	is on "ESC[K" behind the command
		/*
		iPos   = sb.indexOf(CURSOR_BACK_FROM_END);
		if(-1 < iPos) {
			sb.delete(iPos, iPos+CURSOR_BACK_FROM_END.length());
		}
		*/
		sbLast.append(sb);
		}
		return bBack;
	}

	/**
	 * checks for input the Strg+R command
	 *
	 * @param sb		- next data from input
	 * @param sbLast	- Last Command
	 * @param cursorBackFromEnd
	 * @param lettersToDelete2
	 * @param lettersToDelete2
	 * @return
	 */
	private boolean checkStrgR(StringBuilder sb, StringBuilder sbLast, int cursorRight, int lettersToDelete, int cursorBackFromEnd) {
		boolean bBack = false;
		// reverse search is active when these method is called
		//
		int iPosIp = sb.indexOf("@ip");
		if(-1 < iPosIp) {
			// here we get only a prompt string like
			// "[6@[ec2-user@ip-172-31-3-88 ~]$[C[C""
			//
			// this comes instead of CR !
		} else if(checkOfOnlyBS(sb)) {
			// do nothing, is only for cursor positioning
		} else {
			// here we get a string like
			//	"p': pwd" or
			//  "[C[C[C-tr[K" or
			//  "c': [3Pp': pwd"
			//	"[23@l': ll /etc/init.d/single"
			// Check for prompt before the command
			int iPosPrompt 			= sb.indexOf(STRING_STRG_R_PROMT_PART);
			if(-1 < iPosPrompt) {
				int iPosK = sb.indexOf("@");
				if(-1 < iPosK) {
					sb.delete(0, iPosK+1);
				}
			}
			int iPosCursorRight 	= 0;
//			int cursorRight         = getCursorRght(sb);
//			int cursorBackFromEnd 	= getCursorBackFromEnd(sb);
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
						bBack = true;
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
					bBack = true;
				} else {
					// delete all behind startPosition
					sbLast.delete(startPosition, sbLast.length());
					sbLast.append(sb);
					bBack = true;
				}
			} else {
				sbLast.append(sb);
				bBack = true;
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
		return bBack;
	}

	/**
	 * Checks if sb contains only BS
	 * Used for StrgR command
	 *
	 * @param sb	- input from Terminal
	 * @return
	 */
	private boolean checkOfOnlyBS(StringBuilder sb) {
		boolean bBack = true;
		for(int i = 0; i < sb.length(); i++ ) {
			if(BS != sb.charAt(i)) {
				bBack = false;
				break;
			}
		}
		return bBack;
	}

	/**
	 * check for inserting a Key into the command line
	 * Input like :
	 * 	"awd" - insert an a
	 *  "wd"  - delete on sign before "wd"
	 * 	""  - cursor three diget back
	 *
	 * The meaning of it is;
	 * Go back two steps from backward, than insert the Letter "a" before "wd"
	 *
	 * @param sb	- input from Terminal
	 * @param lettersToDelete
	 * @param sbLast- Last Command
	 * @return
	 */
	private boolean checkInsertKey(StringBuilder sb, StringBuilder sbLast, int lettersToDelete) {
		boolean bBack  = false;
		StringBuilder sbLocal = new StringBuilder(sb);
		int iPossb     = 0;
		int iCount     = 0;
		while ((0 < sbLocal.length()) && CHAR_BS.contains(String.valueOf(sbLocal.charAt(iPossb=sbLocal.length()-1)))) {
			sbLocal.deleteCharAt(iPossb);
			iCount++;
		}
		if(0 < lettersToDelete) {
			iPossb = sbLast.lastIndexOf(sbLocal.toString());
			if(0 < iPossb) {
				sbLast.deleteCharAt(iPossb-1);
				bBack = true;
			}
		} else if(0 < iCount) {
			if(0 < sbLocal.length()) {
				char insKey = sbLocal.charAt(0);
				sbLocal.deleteCharAt(0);
				if(-1 <(iPossb=sbLast.lastIndexOf(sbLocal.toString()))) {
					sbLast.insert(iPossb, insKey);
					bBack = true;
				} else {
					iPossb = 0; // for save in cursor Position
				}
			} else {
				bBack = true;	// Nothing to insert, so it's alo ok
			}
		}
		if(bBack) {
			setCursorPosition(sbLast, iPossb);
		}
		return bBack;
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
	private static int getCursorDeleteFromPositionToEnd(StringBuilder sb) {
		//
		//	is on "ESC[K" behind the command
		int iPos   = sb.indexOf(CURSOR_DELETE_FROM_POSITION_TO_END);
		if(-1 < iPos) {
			sb.delete(iPos, iPos+CURSOR_DELETE_FROM_POSITION_TO_END.length());
		}
		return iPos;
	}

	/**
	 * Starts at index i and searches for digit value
	 *
	 * @param i	-	Position in sb
	 * @param j
	 * @param sb	- input from Terminal
	 * @return String with digits
	 */
	private static int getNumberFromUntilP(int iPosEsc, int len, StringBuilder sb) {
		int lettersToDelete = 0;
		String number = "";
		boolean bFoundEsc = false;
		for( int i=iPosEsc+len ; i<sb.length(); i++) {
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
			int iEnd = iPosEsc + len + number.length()+1;
			sb.delete(iPosEsc, iEnd);
			lettersToDelete = Integer.valueOf(number);
		}
		return lettersToDelete;
	}

	/**
	 * Checks for "[1P", count them and delete it in sb
	 * @param sb	- input from Terminal
	 * @return	number of found occurrence
	 */
	private static int getDeleteCharactersBackFromPos(StringBuilder sb) {
		int lettersToDelete = 0;
		//
		// Now search for "ESC[xP"
		if(3 < sb.length()) {
			int iPosEsc = sb.indexOf(CURSOR_DELETE_CHARACTERS_BACK_FROM_POS);
			if(-1 != iPosEsc) {
				lettersToDelete = getNumberFromUntilP(iPosEsc, CURSOR_DELETE_CHARACTERS_BACK_FROM_POS.length(), sb);
			}
		}
		return lettersToDelete;
	}

	/**
	 * Checks for "[C", count them and delete it in sb
	 *
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

}
