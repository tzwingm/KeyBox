package com.keybox.manage.util;


public class SessionOutputCommand {

	private StringBuilder sbLast = new StringBuilder();

//	private static final int BEL = 7;
	private static final int BS  = 8;
	private static final int ESC = 27;
	private static final String CHAR_BS      				           = Character.toString((char)BS);
//	private static final String CHAR_BEL      				           = Character.toString((char)BEL);
//	private static final String CHAR_ESC      				           = Character.toString((char)ESC);
//	private static final String CURSOR_RIGHT 				           = Character.toString((char)ESC)+"[C";
//	private static final String CURSOR_DELETE_FROM_POSITION_TO_END     = CHAR_ESC+"[K";
//	private static final String CURSOR_DELETE_CHARACTERS_BACK_FROM_POS = CHAR_ESC+"[";  // n"P"
//	private static final String STRING_STRG_R_INSERT_KEY               = CHAR_ESC+"[";	// n@	 next letter is that for insert
//	private static final String STRING_STRG_R_INSERT_MASK              = "': ";		// next letter is that for insert
	private static final String STRING_NUMBER_DELIMITER                = "P@";		// next letter is that for insert

//    private static boolean activeBell  = false;
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
//		activeBell  = activeBell2;
		if(activeStrgR != activeStrgR2) {
			cursorPosition = 0;
			clean(sbLast);
			activeStrgR = activeStrgR2;
		}
		System.out.println("evaluateInput-start activeBell, activeStrgR, sbLast <"+Boolean.valueOf(activeBell2).toString()+","+Boolean.valueOf(activeStrgR2).toString()+","+sbLast.toString()+">");
		getCursorPosition(sb);
		//
		// now search for ESC sequences
		while(0 < sb.length()) {
			int iEnd = 0;
			switch(sb.charAt(0)) {
			case BS:	// BS behind the text, comes mostly from StrgR command for the cursor position
			{
				getCursorPosition(sb);
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
