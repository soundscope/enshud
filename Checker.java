package enshud.s3.checker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;




public class Checker {

	/**
	 * サンプルmainメソッド．
	 * 単体テストの対象ではないので自由に改変しても良い．
	 */
	public static void main(final String[] args) {
		// normalの確認
		new Checker().run("tmp/abc.ts");
		//new Checker().run("data/ts/normal08.ts");

		// synerrの確認
		//new Checker().run("data/ts/synerr04.ts");
		//new Checker().run("data/ts/synerr02.ts");

		// semerrの確認
		//new Checker().run("data/ts/semerr01.ts");
		//new Checker().run("data/ts/semerr02.ts");
	}

	/**
	 * TODO
	 * 
	 * 開発対象となるChecker実行メソッド．
	 * 以下の仕様を満たすこと．
	 * 
	 * 仕様:
	 * 第一引数で指定されたtsファイルを読み込み，意味解析を行う．
	 * 意味的に正しい場合は標準出力に"OK"を，正しくない場合は"Semantic error: line"という文字列とともに，
	 * 最初のエラーを見つけた行の番号を標準エラーに出力すること （例: "Semantic error: line 6"）．
	 * また，構文的なエラーが含まれる場合もエラーメッセージを表示すること（例： "Syntax error: line 1"）．
	 * 入力ファイル内に複数のエラーが含まれる場合は，最初に見つけたエラーのみを出力すること．
	 * 入力ファイルが見つからない場合は標準エラーに"File not found"と出力して終了すること．
	 * 
	 * @param inputFileName 入力tsファイル名
	 */


	/**
	 * TODO
	 * 
	 * 開発対象となるCompiler実行メソッド．
	 * 以下の仕様を満たすこと．
	 * 
	 * 仕様:
	 * 第一引数で指定されたtsファイルを読み込み，CASL IIプログラムにコンパイルする．
	 * コンパイル結果のCASL IIプログラムは第二引数で指定されたcasファイルに書き出すこと．
	 * 構文的もしくは意味的なエラーを発見した場合は標準エラーにエラーメッセージを出力すること．
	 * （エラーメッセージの内容はChecker.run()の出力に準じるものとする．）
	 * 入力ファイルが見つからない場合は標準エラーに"File not found"と出力して終了すること．
	 * 
	 * @param inputFileName 入力tsファイル名
	 * @param outputFileName 出力casファイル名
	 */
	List<String> buffer;
	List<String []> splittedBuffer;
	List<String> variableQueue;
	int index;
	Error errorCode;
	String[] st;
	int maxDepth;
	String nowName;

	Map<String,Map<String, String>> nameSpaces;

	String mainProgram;
	String subPrograms;
	String tailProgram;

	protected enum Error {
		NULL,
		PARSE,
		CHECK,
	};

	public void run(final String inputFileName) {
		index = 0;
		buffer = null;
		maxDepth = 0;
		errorCode = Error.NULL;
		splittedBuffer = new ArrayList<String []>();
		variableQueue = new ArrayList<String>();
		nameSpaces = new HashMap<String,Map<String, String>  >();
		nameSpaces.put("main", new HashMap<String, String>());
		nowProgramName = "main";
		try {
			buffer = Files.readAllLines(Paths.get(inputFileName));
		} catch (IOException e) {
			System.err.println("File not found");
			return;
		}
		for(String x: buffer) {
			splittedBuffer.add(x.split("\t"));
			System.out.println(x);
		}

		// TODO
		/*
		for(String[] x: splittedBuffer) {
			for (String y: x) {
				System.out.print(y);
			}
			System.out.println();
		}
		//*/
		if (parse())  	System.out.println("OK");
		else if(errorCode == Error.PARSE) System.err.println("Syntax error: line " + splittedBuffer.get(maxDepth)[3]);
		else if(errorCode == Error.CHECK) System.err.println("Semantic error: line " + splittedBuffer.get(maxDepth)[3]);
	}

	protected boolean program() {
		st = getToken();
		// program

		if(! st[2].equals("17")) {
			raiseError(Error.PARSE);
			return false;
		}

		if(! programName()) {
			raiseError(Error.PARSE);
			return false;
		}
		st = getToken();

		// ;
		if(! st[2].equals("37")) {
			raiseError(Error.PARSE);
			return false;
		}

		if(! block()) {
			raiseError(Error.PARSE);
			return false;
		}
		nowProgramName = "main";

		if(! compoundSentence()) {
			raiseError(Error.PARSE);
			return false;
		}

		st = getToken();
		// .
		if(! st[2].equals("42")) {
			raiseError(Error.PARSE);
			return false;
		}
		
		appendDS();
		appendCONSTCHAR();
		appendLIBBUF();
		return true;
	}

	private void appendLIBBUF() {
		tailProgram += "LIBBUF DS 256;";
	}

	private void appendCONSTCHAR() {
		// TODO Auto-generated method stub
		
	}

	private void appendDS() {
		// TODO Auto-generated method stub
		tailProgram += "VAR DS 256;"; //caution
	}

	protected boolean programName() {
		st = getToken();
		// name
		if(! st[2].equals("43")) {
			raiseError(Error.PARSE);
			return false;
		}
		return true;
	}

	protected boolean block() {
		if(! variableDeclaration("main", true)) {
			raiseError(Error.PARSE);
			return false;
		}

		if(! subProgramDeclarations()) {
			raiseError(Error.PARSE);
			return false;
		}

		return true;
	}

	protected boolean variableDeclaration(String caller, boolean declare) {
		int save_index = index;
		st = getToken();
		//var
		if(st[2].equals("21")) {
			if(! variableDeclarationList(caller, declare)) {
				return false;
			}
		} else {
			index = save_index;
		}

		return true;
	}

	protected boolean variableDeclarationList(String caller, boolean declare) {
		if(! variableNameList(caller, declare)) {
			return false;
		}
		st = getToken();
		//:
		if(! st[2].equals("38")) {
			return false;
		}
		if(! type()) {
			return false;
		}

		st = getToken();
		// ;
		if(! st[2].equals("37")) {
			return false;
		}
		for(;;) {
			int save_index = index;
			//declare
			if(variableNameList(caller, declare)) {
				st = getToken();
				//:
				if(! st[2].equals("38")) {
					return false;
				}
				if(! type()) {
					return false;
				}
				st = getToken();
				//;
				if(! st[2].equals("37")) {
					return false;
				}
			} else {
				index = save_index;
				break;
			}
		}
		return true;
	}

	protected boolean variableNameList(String caller, boolean declare) {
		String ret;
		if((ret = variableName(caller, declare)).equals("false")) {
			return false;
		}
		for(;;) {
			int save_index = index;
			st = getToken();
			if(st[2].equals("41")) {
				if(variableName(caller, declare).equals("false")) {
					return false;
				}
			} else {
				index = save_index;
				break;
			}
		}
		return true;
	}

	protected String variableName(String caller, boolean declare) {

		st = getToken();
		// name
		if(! st[2].equals("43")) {
			return "false";
		}

		if(declare) {
			if(nameSpaces.get(caller).get(st[0]) == null) {
				nameSpaces.get(caller).put(st[0], "");
				//nowName = st[0];
				variableQueue.add(st[0]);
			} else {
				raiseError(Error.CHECK);
				return "false";
			}
		} else {
			if(nameSpaces.get(caller).get(st[0]) != null) {
				return nameSpaces.get(caller).get(st[0]);
			} else {
				if(nameSpaces.get("main").get(st[0]) != null ) {
					return nameSpaces.get("main").get(st[0]);
				} else {
					raiseError(Error.CHECK);
					return "false";
				}
			}
		}
		return "true";
	}

	protected boolean type() {
		int save_index = index;

		if(! standardType(false)) {
			index = save_index;
			if(! arrayType()) {
				return false;
			} else {
				return true;
			}
		}
		return true;
	}	

	protected boolean standardType(boolean caller) {
		st = getToken();

		for(String nowName: variableQueue) {
			if(! caller) {
				//boolean, char, integer
				if(st[2].equals("3")) {
					nameSpaces.get(nowProgramName).put(nowName, "boolean");
				}
				if(st[2].equals("4")) {
					//System.err.println(nowName);

					nameSpaces.get(nowProgramName).put(nowName, "char");
				}
				if(st[2].equals("11")) {
					nameSpaces.get(nowProgramName).put(nowName, "integer");
				}
			} else {
				//boolean, char, integer of array
				if(st[2].equals("3")) {
					nameSpaces.get(nowProgramName).put(nowName, "Aboolean");
				}
				if(st[2].equals("4")) {
					nameSpaces.get(nowProgramName).put(nowName, "Achar");
				}
				if(st[2].equals("11")) {
					nameSpaces.get(nowProgramName).put(nowName, "Ainteger");
				}
			}
		}
		if(st[2].equals("3") || st[2].equals("4") || st[2].equals("11")) {
			variableQueue = new ArrayList<String>();
			return true;
		}
		return false;
	}

	protected boolean arrayType() {
		st = getToken();
		//array
		if(! st[2].equals("1")) {
			return false;
		}
		st = getToken();
		// [
		if(! st[2].equals("35")) {
			return false;
		}
		if(! indexMinimum() ) {
			return false;
		}
		st = getToken();
		// ..
		if(! st[2].equals("39")) {
			return false;
		}
		if(! indexMaximum()) {
			return false;
		}
		st = getToken();
		// ]
		if(! st[2].equals("36")) {
			return false;
		}
		st = getToken();
		// of
		if(! st[2].equals("14")) {
			return false;
		}
		if(! standardType(true)) {
			return false;
		}
		return true;
	}

	protected boolean indexMinimum() {
		if(! integer()) {
			return false;
		}
		return true;
	}

	protected boolean indexMaximum() {
		if(! integer()) {
			return false;
		}
		return true;
	}

	protected boolean integer() {
		int checkInt = 1;
		int save_index = index;
		st = getToken();
		// + or -
		if(st[2].equals("30") ) {
			;
		} else if (st[2].equals("31")) {
			checkInt *= -1;
		} else {
			index = save_index;
		}

		st = getToken();
		// unsigned int
		if (! st[2].equals("44")) {
			return false;
		}
		checkInt *= Integer.parseInt(st[0]);
		if (!(-32768 <= checkInt && checkInt <= 32767)) {
			raiseError(Error.CHECK);
			return false;
		}
		return true;
	}

	String nowProgramName;

	protected boolean subProgramDeclarations() {
		for(;;) {
			int save_index = index;

			if(subProgramDeclaration()) {
				st = getToken();
				// ;

				if(! st[2].equals("37")) {
					return false;
				}
			} else {
				index = save_index;
				break;
			}
		}
		return true;
	}

	protected boolean subProgramDeclaration() {

		if(! subProgramHeader()) {
			return false;
		}
		if(! variableDeclaration(nowProgramName,true)) {
			return false;
		}
		if(! compoundSentence()) {
			return false;
		}

		return true;
	}

	protected boolean subProgramHeader() {
		st = getToken();
		// procedure
		if(! st[2].equals("16")) {
			return false;
		}

		if(procedureName(true).equals("false")) {
			return false;
		} 

		if(! formalParameter(true)) {
			return false;
		}

		st = getToken();
		// ;
		if(! st[2].equals("37")) {
			return false;
		}
		return true;
	}

	protected String procedureName(boolean declare) {
		st = getToken();
		// name
		if(! st[2].equals("43")) {
			return "false";
		}
		if(declare) {
			if(nameSpaces.get(st[0]) == null) {
				nameSpaces.put(st[0], new HashMap<String ,String>());
				nowProgramName = st[0];
				variableQueue = new ArrayList<String>();
			} else {
				raiseError(Error.CHECK);
				return "false";
			}
		} else {
			if(nameSpaces.get(st[0]) == null) {
				raiseError(Error.CHECK);
				return "false";
			} else {
				return st[0];
			}
		}
		
		return "true";
	}

	protected boolean formalParameter(boolean declare) {
		int save_index = index;
		st = getToken();
		// (
		if(st[2].equals("33")) {

			if(! formalParameterList(declare)) {
				return false;
			}

			st = getToken();
			if(! st[2].equals("34")) {
				return false;
			}
		} else {
			index = save_index;
		}

		return true;
	}

	protected boolean formalParameterList(boolean declare) {
		if(! formalParameterNameList(declare)) {
			return false;
		}
		st = getToken();

		// :
		if(! st[2].equals("38")) {
			return false;
		}
		if(! standardType(false)) {
			return false;
		}

		for(;;) {
			int save_index = index;
			st = getToken();
			// ;
			if(st[2].equals("37")) {
				if(! formalParameterNameList(declare)) {
					return false;
				}
				st = getToken();
				// :
				if(! st[2].equals("38")) {
					return false;
				}
				if(! standardType(false)) {
					return false;
				}
			} else {
				index = save_index;
				break;
			}
		}
		return true;
	}

	protected boolean formalParameterNameList(boolean declare) {
		if(! formalParameterName(declare)) {
			return false;
		}

		for(;;) {
			int saved_index = index;
			st =  getToken();
			//,
			if(st[2].equals("41")) {
				if(! formalParameterName(declare)) {
					return false;
				}	
			} else {
				index = saved_index;
				break;
			}
		}
		return true;
	}

	protected boolean formalParameterName(boolean declare) {
		st = getToken();
		// name
		if(! st[2].equals("43")) {
			return false;
		}
		if(declare) {
			if(nameSpaces.get(nowProgramName).get(st[0]) == null) {
				nameSpaces.get(nowProgramName).put(st[0], "");
				//nowName = st[0];
				variableQueue.add(st[0]);
			} else {
				raiseError(Error.CHECK);
				return false;
			}
		}
		return true;
	}

	protected boolean compoundSentence() {
		st = getToken();
		//begin

		if(! st[2].equals("2")) {
			return false;
		}

		if(! sentenceList()) {
			return false;
		}

		st = getToken();

		//end
		if(! st[2].equals("8")) {
			return false;
		}
		return true;
	}

	protected boolean sentenceList() {

		if(! sentence()) {
			return false;
		}

		st = getToken();
		// ;
		if(! st[2].equals("37")) {
			return false;
		}

		for(;;) {
			int save_index = index;

			if(sentence()) {

				st = getToken();

				//;
				if(! st[2].equals("37")) {
					return false;
				}


			} else {

				index = save_index;
				break;
			}
		}
		return true;
	}

	protected boolean sentence() {
		int save_index = index;
		if(! baseSentence()) {

			index = save_index;

			st = getToken();
			//while
			if(st[2].equals("22")) {
				if (! expression("").equals("boolean")) {
					raiseError(Error.CHECK);
					return false;
				}
				st = getToken();
				//do
				if (! st[2].equals("6")) {
					return false;
				}
				if (! compoundSentence()) {
					return false;
				}
				//if
			} else if( st[2].equals("10")) {
				String tmp;
				if(! (tmp = expression("")).equals("boolean")) {
					raiseError(Error.CHECK);
					return false;
				}

				st = getToken();

				//then
				if(! st[2].equals("19")) {
					return false;
				}
				if(! compoundSentence()) {
					return false;
				}
				// else
				int save_index2 = index;
				st = getToken();

				if(st[2].equals("7")) {
					if(! compoundSentence()) {
						return false;
					}
				} else {
					index = save_index2;
				}
			} else {
				return false;
			}
		}

		return true;
	}

	protected boolean baseSentence() {
		int save_index = index;
		if(! assignmentSentence()) {
			index = save_index;

			if(! procedureCallSentence() ) {
				index = save_index;


				if(! inputOutputSentence()) {
					index = save_index;

					if(! compoundSentence()) {
						return false;
					}

				}

			}
		}

		return true;
	}

	protected boolean inputOutputSentence() {
		st = getToken();

		// readln
		if(st[2].equals("18")) {
			int save_index = index;
			st = getToken();
			//(
			if(st[2].equals("33")) {
				if(! variableList(false)) {
					return false;
				}

				st = getToken();
				if(! st[2].equals("34")) {
					return false;
				}
			} else {
				index = save_index;
				return true;
			}
			//writeln
		} else if(st[2].equals("23")) {
			int save_index = index;
			st = getToken();
			//(
			if(st[2].equals("33")) {
				if(! expressionList("writeln")) {
					return false;
				}

				st = getToken();
				if(! st[2].equals("34")) {
					return false;
				}
				if(nowProgramName.equals("main")) {
					mainProgram += "  CALL  WRTSTR\n";
					mainProgram += "  CALL  WRTLN\n";
				} else {	
					subPrograms += "  CALL  WRTSTR\n";
					subPrograms += "  CALL  WRTLN\n";
				}
			} else {
				index = save_index;
				return true;
			}
		} else {
			return false;
		}
		//dead code
		return true;
	}

	protected boolean variableList(boolean declare) {
		if(variable(declare).equals("false")) {
			return false;
		}
		for(;;) {
			int save_index = index;
			if(st[2].equals("41")) {
				if(variable(declare).equals("false")) {
					return false;
				}
			} else {
				index = save_index;
				break;
			}
		}
		return true;
	}

	protected boolean assignmentSentence() {
		String ret;
		if((ret = leftExpression()).equals("false")) {
			return false;
		}
		st = getToken();
		// :=
		if(! st[2].equals("40")) {
			raiseError(Error.PARSE);
			return false;
		}
				
		if(! expression("").equals(ret)) { //no Name
			raiseError(Error.CHECK);
			return false;
		}
		return true;
	}

	protected String leftExpression() {
		String ret;
		if((ret = variable(false)).equals("false")) {
			return "false";
		}
		return ret;
	}

	//should use left factoring
	protected String variable(boolean declare) {
		int save_index = index;
		String ret;
		if((ret = indexedVariable(declare)).equals("false")) {
			index = save_index;
			if((ret =  pureVariable(declare)).equals("false")) {
				return "false";
			} else {
				return ret;
			}
		} 
		return ret;
	}

	protected String pureVariable(boolean declare) {
		String ret;
		if((ret = variableName(nowProgramName,declare)).equals("false")) {
			return "false";
		}
		return ret;
	}

	protected String indexedVariable(boolean declare) {
		String ret;
		if((ret = variableName(nowProgramName,declare)).equals("false")) {
			return "false";
		}
		
		st = getToken();
		//[
		if(! st[2].equals("35")) {
			return "false";
		}

		if(! index()) {
			return "false";
		}

		st = getToken();
		//]
		if(! st[2].equals("36")) {
			return "false";

		}
		return ret.replace("A",	"");
	}

	protected boolean index() {

		if(! expression("").equals("integer")) { //no name
			raiseError(Error.CHECK);
			return false;
		}

		return true;
	}

	protected boolean procedureCallSentence() {
		String retName; 
		if((retName = procedureName(false)).equals("false")) {
			return false;
		}

		int save_index = index;
		st = getToken();
		//(
		if(st[2].equals("33")) {

			if(! expressionList(retName)) {
				return false;
			}

			st = getToken();
			// )
			if(! st[2].equals("34")) {
				return false;
			}
		} else {
			index = save_index;
		}
		return true;
	}

	protected boolean expressionList(String procedureName) {
		String ret;
		if((ret = expression(procedureName)).equals("false")) {
			return false;
		} else if(ret.equals("str") && procedureName.equals("writeln")){
			
		}

		for(;;) {
			int save_index = index;
			st = getToken();
			// ,
			if(st[2].equals("41")) {
				if(expression(procedureName).equals("false")) {
					return false;
				}
			} else {
				index = save_index;
				break;
			}
		}
		return true;
	}

	protected String expression(String procedureName) {
		String ret1, ret2;
		if((ret1 = simpleExpression(procedureName)).equals("false")) {
			raiseError(Error.PARSE);
			return "false";
		}

		int save_index = index;

		if(relationOperator()) {
			if((ret2 = simpleExpression(procedureName)).equals("false")) {
				raiseError(Error.PARSE);
				return "false";
			}
			if (! ret1.equals(ret2)) {
				raiseError(Error.CHECK);
				return "false";
			}
			return "boolean";
		} else {
			index = save_index;
		}
		return ret1;
	}

	protected String simpleExpression(String procedureName) {
		int save_index = index;
		st = getToken();
		String ret1, ret2;
		// + or -
		if(st[2].equals("30") || st[2].equals("31")) {
			;
		} else {
			index = save_index;
		}

		if((ret1 = term(procedureName)).equals("false") ) {
			return "false";
		}

		for(;;) {
			int save_index2 = index;
			if(additiveperator()) {
				if((ret2 = term(procedureName)).equals("false")) {
					return "false";
				}
				if (! ret1.equals(ret2)) {
					raiseError(Error.CHECK);
					return "false";
				}
			} else {
				index = save_index2;
				break;
			}
		}
		return ret1;
	}

	protected String term(String procedureName) {
		String ret1, ret2;
		if((ret1 = divisor(procedureName)).equals("false")) {
			return "false";
		}

		for(;;) {
			int save_index = index;
			if (multiplicativeOperator()) {
				if ((ret2 = divisor(procedureName)).equals("false")) {
					return "false";
				}
				if (!ret1.equals(ret2)) {
					raiseError(Error.CHECK);
					return "false";
				}
			} else {
				index = save_index;
				break;
			}
		}

		return ret1;
	}

	protected String divisor(String procedureName) {
		int save_index = index;
		String ret;
		String varRet;
		if((varRet = variable(false)).equals("false")) {
			index = save_index;
			if((ret = constant()).equals("false")) {
				index = save_index;

				st = getToken();
				// (
				if(st[2].equals("33")) {
					if( (ret = expression(procedureName)).equals("false")) {
						return "false";
					}
					st = getToken();
					// )
					if(! st[2].equals("34")) {
						return "false";
					}
					return ret;
					//not
				} else if(st[2].equals("13")) {
					if(! (ret = divisor(procedureName)).equals("boolean")) {
						return "false";
					} else {
						return "boolean";
					}
				} else {
					return "false";
				}
			} else {
				if (procedureName.equals("writeln") && ret.equals("char")) {
					return "str";
				} else {
					return ret;
				}
			}
		} else  {
			return varRet;
			//variable

		}
		//dead code
		//return "true";
	}

	protected String constant() {
		st = getToken();
		// unsigned int
		if (st[2].equals("44")) {
			return "integer";
			//string
		} else if(st[2].equals("45")) {
			if(st[0].length() == 3) {
				return "char";
			} else {
				return "str";
			}
			// false
		} else if(st[2].equals("9")) {
			return "boolean";
			// true
		} else if(st[2].equals("20")) {
			return "boolean";
		}
		return "false";
	}

	protected boolean multiplicativeOperator() {
		st = getToken();
		// "*" | "/" | "div" | "mod" | "and".
		if(! (st[2].equals("32") || st[2].equals("5") ||  st[2].equals("12") || st[2].equals("0")) ) {
			return false;
		}

		return true;
	}

	protected boolean relationOperator() {
		st = getToken();
		// = or <> or < or <= or > or >=
		if(! (st[2].equals("24") || st[2].equals("25") ||  st[2].equals("26") 
				|| st[2].equals("27") || st[2].equals("29") || st[2].equals("28")) ) {
			return false;
		}

		return true;
	}

	protected boolean additiveperator() {
		st = getToken();
		// + || - || or
		if(!(st[2].equals("30") || st[2].equals("31") || st[2].equals("15"))) {
			return false;
		} 
		return true;
	}

	protected boolean parse() {
		return program();
	}

	protected String[] getToken() {
		//System.out.println(index + " "+ splittedBuffer.get(index)[0]);
		maxDepth = maxDepth < index ? index : maxDepth; 
		return splittedBuffer.get(index++);
	}
	
	protected void raiseError(Error e) {
		if (errorCode == Error.NULL) errorCode = e;
	}

	
	
	
}
