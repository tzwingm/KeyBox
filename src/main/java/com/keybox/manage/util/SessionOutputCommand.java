package com.keybox.manage.util;


public class SessionOutputCommand {

	private StringBuilder sbLast = new StringBuilder();

	private static final int BEL = 7;
	private static final int BS  = 8;
	private static final int ESC = 27;
	private static final String CHAR_BS      				           = Character.toString((char)BS);
	private static final String CHAR_BEL      				           = Character.toString((char)BEL);
	private static final String CHAR_ESC      				           = Character.toString((char)ESC);
	private static final String CURSOR_RIGHT 				           = Character.toString((char)ESC)+"[C";
	private static final String CURSOR_DELETE_FROM_POSITION_TO_END     = CHAR_ESC+"[K";
	private static final String CURSOR_DELETE_CHARACTERS_BACK_FROM_POS = CHAR_ESC+"[";  // n"P"
	private static final String STRING_STRG_R_INSERT_KEY               = CHAR_ESC+"[";	// n@	 next letter is that for insert
	private static final String STRING_STRG_R_INSERT_MASK              = "': ";		// next letter is that for insert
	private static final String STRING_NUMBER_DELIMITER                = "P@";		// next letter is that for insert

    private static boolean activeBell  = false;
    private static boolean activeStrgR = false;
    private int cursorPosition = 0;

	/**
	 * Adds one character to
	 * @param inputLine
	 * @param sb
	 */
	public void adChar(StringBuilder sb) {
		if(1 == sb.length()) {
			if(CHAR_BS.contains(sb)) {
				cursorPosition--;
			} else {
				sbLast.append(sb);
				cursorPosition++;
			}
			System.out.println("adChar - sbLast start, adding <"+sbLast.toString()+" - "+sb.toString()+">");
		}
	}

	/**
	 * Evaluate the snippet from input
	 * @param sb			- next data from input
	 * @param activeBell2	- we have active BEL / TAB
	 * @param activeStrgR2	- we have active StrgR
	 */
	public void evaluateInput(StringBuilder sb, boolean activeBell2, boolean activeStrgR2) {
		//
		// find command for the result from the host
		activeBell  = activeBell2;
		if(activeStrgR != activeStrgR2) {
			cursorPosition = 0;
			clean(sbLast);
			activeStrgR = activeStrgR2;
		}
		System.out.println("evaluateInput-start activeBell, activeStrgR, sbLast <"+Boolean.valueOf(activeBell2).toString()+","+Boolean.valueOf(activeStrgR2).toString()+","+sbLast.toString()+">");
		int cp 	= getCursorPosition(sb);
		//
		// now search for ESC sequences
		while(0 < sb.length()) {
			int iEnd = 0;
			switch(sb.charAt(0)) {
			case BS:	// BS behind the text, comes mostly from StrgR command for the cursor position
			{
				cp 	= getCursorPosition(sb);
				break;
			}
			case ESC:	// start of several sequences
			{
				if((2 < sb.length()) && ('[' == sb.charAt(1))) {
					sb.delete(0, 2);	// delete ESC[
					switch(sb.charAt(0)) {
					case 'C':	// cursor right
					{
						sb.delete(0, 1);
						if(cursorPosition < sbLast.length()) {
							cursorPosition++;
						}
						break;
					}
					case 'K':	// delete from cursor to end
					{
						sbLast.delete((int) cursorPosition, sbLast.length());
						sb.delete(0, 1);
						break;
					}
					default:
					{
						StringBuilder delimiterFound = new StringBuilder();
						//
						// look for
						// CHAR_ESC+"[";  // n"P"
						// CHAR_ESC+"[";  // n@	 next letter is that for insert
						int number = getNumberFromUntil(sb, new StringBuilder(STRING_NUMBER_DELIMITER), delimiterFound);
						if(-1 < number) {
							switch(delimiterFound.charAt(0)) {
							case 'P':
								iEnd = cursorPosition+number;
								sbLast.delete(cursorPosition, iEnd);
								break;
							case '@':
								for(int j=0; j < number; j++) {
									sbLast.insert(cursorPosition, 0);
								}
								break;
							default:
								break;
							}
						}
						break;
					}
					}
				}
				break;
			}
			default:
			{
				String s = getText(sb);
				iEnd = cursorPosition+s.length();
				sbLast.replace(cursorPosition, iEnd, s);
				cursorPosition = iEnd;
				break;
			}
			}
		}
		System.out.println("evaluateInput - end sbLast, sb, cursorPosition <"+sbLast.toString()+"-- , --"+sb.toString()+"-- , --"+cursorPosition+">");
		return;
	}

	/**
	 * String should start with an letter and end with BS or ESC
	 *
	 * @param sb		- next data from input
	 * @return
	 */
	private String getText(StringBuilder sb) {
		String s = "";
		boolean   bEndswithSequence = false;
		for(int i=0; i < sb.length(); i++) {
			switch(sb.charAt(i)) {
			case BS:
			case ESC:
				bEndswithSequence = true;
				break;
			default:
			{
				s += sb.charAt(i);
				break;
			}
			}
			// loop ?
			if(bEndswithSequence) {
				break;	// end for loop
			}
		}
		if( 0 < s.length()) {
			sb.delete(0, s.length());
		}
		return s;
	}

	/**
	 * Gets the Position in sbLast in relation to the count of BS from start in sb
	 *
	 * @param sb		- next data from input
	 * @return -1, for no BS in start of sb
	 */
	private int getCursorPosition(StringBuilder sb) {
		int cP = cursorPosition;
		if( (0 < sb.length()) && (BS == sb.charAt(0)) ) {
			int countOfBSbeforeText = getCountOfBSbeforeText(sb);
			if(-1 < countOfBSbeforeText) {
				sb.delete(0, countOfBSbeforeText);
				if(activeStrgR) {
					cP -= countOfBSbeforeText;
				} else {
					cP = sbLast.length()-countOfBSbeforeText;
				}
			}
		}
		if(0 > cP) {	// BS given, take new computed position
			cursorPosition = 0;
		} else if(cursorPosition > sbLast.length()) {
			cursorPosition = sbLast.length();	// set defined to
		} else {
			cursorPosition = cP;
		}
		return cursorPosition;
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
	private boolean checkStrgR(StringBuilder sb, int cursorRight, int lettersToDelete, int cursorBackFromEnd) {
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
			int iPosCursorRight 	= 0;
			int bsCount             = getBs(sb);
			// Check for prompt before the command
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
				bBack = true;	// Nothing to insert, so it's also ok
			}
		}
		if(bBack) {
			setCursorPosition(sbLast, iPossb);
		}
		return bBack;
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

		if(0 > cursorPosition) {
			cursorPosition = 0;
		}
		Character charInsert    = getStrgRKey(sb);
		if( (0<cursorCountOfBSbeforeText) || (0<lettersToDelete || (-1 < cursorDeleteFromPositioToEnd))) {
			int iEnd = 0;
			//
			// the input means:
			// go back from the end of <sbLast> for <cursorCountOfBSbeforeText>
			// than skip number of <lettersToDelete>
			// than replace sbLast with the input string <sb>
			getBs(sb);	// clean all BS in input
			if(0<lettersToDelete) {
				iEnd = cursorPosition+lettersToDelete;	// Go back from position
				if(iEnd > sbLast.length()) {
					iEnd = sbLast.length();
				}
				sbLast.delete(cursorPosition, iEnd);
			}
			if(-1 < cursorDeleteFromPositioToEnd) {
				sbLast.delete(cursorPosition, sbLast.length());
			}
			iEnd = cursorPosition+sb.length();
			sbLast.replace(cursorPosition, iEnd, sb.toString());
			bBack = true;

		} else {
			sbLast.append(sb);
		}

		return bBack;
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

	/**
	 * Set the given cursor position to inputline
	 * @param sbLast	- actual, before next, buffer for command
	 * @param cursorPosition
	 */
	private void setCursorPosition(StringBuilder sbLast, int cursorPosition) {
		if(cursorPosition > sbLast.length()) {
			this.cursorPosition = sbLast.length();
		} else if(0 > cursorPosition) {
			this.cursorPosition = 0;
		} else {
			this.cursorPosition = cursorPosition;
		}
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
	 * Starts at index i and searches for digit value
	 *
	 * @param iPosEsc	-	Position in sb
	 * @param len		-   offset from start
	 * @param sb		- 	input from Terminal
	 * @return String with digits
	 */

	/**
	 * get a number starting from sb.charAt(0) until a given delimiter
	 * @param sb				- input from Terminal
	 * @param delimiter			- possible letters behind the number etc : P, @
	 * @param delimiterFound	- as output the found delimiter
	 * @return -1 for nothing found, otherwise the number
	 */
	private static int getNumberFromUntil(StringBuilder sb, StringBuilder delimiter, StringBuilder delimiterFound ) {
		int lettersToDelete = -1;
		String number = "";
		Character 	c = ' ';
		boolean bFoundEsc = false;
		for( int i=0 ; i<sb.length(); i++) {
			c = sb.charAt(i);
			if(!Character.isDigit(c)) {
				if(-1 < delimiter.indexOf(c.toString())) {
					bFoundEsc = true;
					break;
				}
			} else {
				number += c;
			}
		}
		if(bFoundEsc) {
			// Delete characters from input string
			int iEnd = number.length()+1;
			delimiterFound.insert(0, c);
			sb.delete(0, iEnd);
			lettersToDelete = Integer.valueOf(number);
		}
		return lettersToDelete;
	}

	/**
	 * Starts at index i and searches for digit value
	 *
	 * @param iPosEsc	-	Position in sb
	 * @param len		-   offset from start
	 * @param sb		- 	input from Terminal
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
	 * Checks for "[K" and delete it in sb
	 *
	 * @param sb 	- input from Terminal
	 * @param sbLast- last command
	 * @return
	 */
	private static int getCursorDeleteFromPositionToEnd(StringBuilder sb, StringBuilder sbLast) {
		//
		//	count number of "ESC[K"
		int iPos     = 0;
		int iPosBS   = 0;
		while(-1 <(iPos = sb.indexOf(CURSOR_DELETE_FROM_POSITION_TO_END))) {
			//
			// Check for leading BS
			int iLen     = CURSOR_DELETE_FROM_POSITION_TO_END.length();
			if(0 < iPos) {
				iPosBS = iPos-1;
				if(BS == sb.charAt(iPosBS)) {
					iLen++;
					iPos=iPosBS;
				}
			}
			sb.delete(iPos, iPos+iLen);

			if(0 < sbLast.length()) {
				sbLast.deleteCharAt(sbLast.length()-1);
			}
		}
		return iPos;
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
	 * Checks for something like "p': pwd[1@x"
	 * While StrgR is active comes this or  "x': .."
	 *
	 * @param sb	- input from Terminal
	 * @return	Character from Input
	 */
	private static Character getStrgRKey(StringBuilder sb) {
		Character cKey = new Character((char) 0);
		//
		//	is on "ESC[n@" behind the command
		int iPos   = sb.indexOf(STRING_STRG_R_INSERT_KEY);
		if(-1 < iPos) {
			int iPosKl = sb.indexOf("@", iPos);
			if(-1 < iPosKl) {
				String s = sb.substring(iPos+STRING_STRG_R_INSERT_KEY.length(), iPosKl);
				cKey = (char) Integer.valueOf(s).intValue();
				sb.delete(iPos, iPosKl+1);
			}
		}
		return cKey;
	}

	public StringBuilder getSbLast() {
		return sbLast;
	}

	public void setSbLast(StringBuilder sbLastInput) {
		clean(sbLast);
		sbLast.append(sbLastInput);
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
