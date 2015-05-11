package com.keybox.manage.util;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.impl.AvalonLogger;

import com.keybox.manage.db.SessionAuditDB;
import com.keybox.manage.model.SessionOutput;

public class SessionOutputAudit {

	private SessionOutputCommand command = new SessionOutputCommand();

    private static final int KEYSB_CR          = 0;		// CRLF is given
    private static final int KEYSB_STRGC       = 1;		// ^C
    private static final int KEYSB_STRGR       = 2;		// ^C
    private static final int KEYSB_BEL         = 3;		// BEL
    private static final int KEYSB_BEL_CONTENT = 4;		// Text for last BEL
    private static final int KEYSB_DONE        = 5;		// Key is done, no output to database
    private static final int KEYSB_INIT_TERM   = 6;    	// First output on terminal screen
    private static final int KEYSB_INIT_TERM_P = 7;    	// First output prompt on terminal screen
    private static final int KEYSB_WAIT_PROMPT = 8;    	// Answer is not complete jet, wait until prompt
    private static final int KEYSB_UNKNOWN     = -1;	// unknown command or input

	private static final int BEL = 7;
	private static final int ESC = 27;
	private static final String CHAR_ESC      				           = Character.toString((char)ESC);
	private static final String STRING_STRG_R                          = "reverse-i-search";
    private static final String STRING_EC2_USER 					   = "[ec2-user";
    private static final String STRING_DOLLAR_BLANC 				   = "$ ";
//    private static final String NEW_LINE 							   = System.getProperty("line.separator");
    private static final String NEW_LINE 							   = Character.toString((char)13)+"\n";

    private static boolean activeBell  = false;
    private static boolean checkBelCommand = false;
    private static StringBuilder  belCommandBehindDollar = new StringBuilder();
    private static boolean activeStrgR = false;
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
		if(	(2 == sb.length()) && (-1 < sb.indexOf(NEW_LINE)))  {
			System.out.println("setAudit - sb <"+sb.toString()+">");
			System.out.println("setAudit - outputFromCommand <"+outputFromCommand.toString()+">");
			outputFromCommand.append(sb);
		} else if((0 < outputFromCommand.length()) && !checkPrompt(sb)) {
			outputFromCommand.append(sb);
			System.out.println("setAudit - adding sb <"+outputFromCommand.toString()+">");
		} else {
			//
			// select command or output data
			checkBelCommand 				= false;			// If there is something behind "$ " for example "$ pw", then keep old command !
			List<StringBuilder> arrSbLines 	= new ArrayList<>();
			clean(belCommandBehindDollar);
			if(0 < outputFromCommand.length()) {
				sb.insert(0, outputFromCommand);
				clean(outputFromCommand);
			}
			switch(changeInput(inputLine, sb, arrSbLines)) {
			case KEYSB_CR:
			{
				//
				// get the commandline
				sessionOutput.setOutput(command.getSbLast().toString());
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
				clean(outputFromCommand);
			}
			case KEYSB_STRGC:
			{
				if(!activeBell) {
					//				inputLine.set(1, new StringBuilder());
					if( !checkBelCommand) {
						command.setSbLast(belCommandBehindDollar);
					}
				}
				clean(outputFromCommand);
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
				break;
			}
			case KEYSB_WAIT_PROMPT:
			{
				break;
			}
			default:
			{
				break;
			}
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
		System.out.println("changeInput - getting <"+sb.toString()+">");
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
				command.adChar(sb);
			}
			break;
		}
		default:	// All other
		{
			activePrompt 		= false;
			int 	iStart 		= 0;
			int 	iEnd		= sb.indexOf(NEW_LINE);
			//
			// check for an output or command
			while((-1 < iStart) && (-1 < (iEnd = sb.indexOf(NEW_LINE, iStart)))) {
				iEnd += NEW_LINE.length();
				String line = sb.substring(iStart, iEnd);

				arrSbLines.add(new StringBuilder(line));
				iStart = iEnd;
			}
			if(-1 < iStart) {
				arrSbLines.add(new StringBuilder(sb.substring(iStart, sb.length())));
			}
			switch ( arrSbLines.size() ) {
			case 0:
			{
				break;
			}
			case 1:			// one input line for working in the terminal line
			{
				activePrompt = checkPrompt(sb);
				if((-1<sb.indexOf(STRING_STRG_R))) {				// StrgR reverse search
					keySb = KEYSB_STRGR;
					activeStrgR = true;
				} else if(activePrompt) {
					if(0 == inputLine.get(3).length()) {
						inputLine.set(3, sb);	// set first prompt from start
						keySb = KEYSB_INIT_TERM_P;
					} else {
						command.setSbLast(belCommandBehindDollar);
						keySb = KEYSB_CR;
					}
				} else if(0 < sb.length())	{ // One character
					//
					// At this point we get the optimized Inputs
					//
					getBel(sb);
					command.evaluateInput(sb, activeBell, activeStrgR);
				}
				break;
			}
			default:
			{
				boolean loadOutput = false;
				if(0 == sb.indexOf("Last login:")) {			// first message on the terminal
					keySb = KEYSB_INIT_TERM;
					inputLine.set(2, sb);
					loadOutput = true;
					isCommand = false;
				} else if(activeBell) {
					loadOutput = true;
					keySb = KEYSB_CR;
				} else if( activeStrgR) {
					if(2 == arrSbLines.size()) {
						System.out.println("changeInput - skip <"+sb.toString()+">");
					} else {
						loadOutput = true;
						activeStrgR = false;
						keySb = KEYSB_CR;
					}
				} else if(((sb.charAt(0) == 13) && (sb.charAt(1) == 10))) {	// CRLF
					loadOutput = true;
					keySb  = KEYSB_CR;
				} else if( ((sb.charAt(0) == '^') && (sb.charAt(1) == 'C'))) { // StrgC
					loadOutput = true;
					keySb  = KEYSB_STRGC;
				}
				if(loadOutput) {
					outputFromCommand.append(checkOutFromCommand(arrSbLines));
					if(activeBell) {
						command.setSbLast(belCommandBehindDollar);
					}
				}
				if((0 < outputFromCommand.length()) && !activePrompt) {
					keySb  = KEYSB_WAIT_PROMPT;
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
				if(activeStrgR) {
					keySb = KEYSB_CR;
				}
			}
		}

		return keySb;
	}

	/**
	 * checks for on BEL in sb
	 *
	 * @param sb
	 * @return
	 */
	private boolean getBel(StringBuilder sb) {
		if(0 < sb.length()) {
			if( BEL ==sb.charAt(0)) {
				activeBell = true;
				sb.delete(0, 1);
			}
		}
		return activeBell;
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
		if(2 <= sb.length()) {
			if(-1 < sb.indexOf(CHAR_ESC+"]")) {
				bRet = true;
			} else {	// TODO Obsolete ?
				iPosUser   = sb.indexOf(STRING_EC2_USER);
				if(-1 < iPosUser ) {
					iPosDollar = sb.indexOf("$ ", iPosUser);
					if(.1 < iPosDollar) {
						bRet = true;
					}
				}
			}
		}
		if(bRet) {
			activePrompt = true;
		}
		return bRet;
	}

	/**
	 * Cleans up the buffer
	 *
	 * @param sbBuffer
	 */
	private void clean(StringBuilder sbBuffer) {
		if(0 < sbBuffer.length()) {
			sbBuffer.delete(0, sbBuffer.length());
		}
	}

}
