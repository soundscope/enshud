//https://github.com/soundscope/enshud, MIT License

package enshud.s4.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


import enshud.casl.CaslSimulator;
import enshud.s1.lexer.Lexer;

public class Compiler {

	/**
	 * サンプルmainメソッド．
	 * 単体テストの対象ではないので自由に改変しても良い．
	 */
	public static void main(final String[] args) {
		long time = 0;
		int target = 19;


		// Compilerを実行してcasを生成する
		/*
		for(int i = 1; i <= 20; i++) { 
			if(i == target) {
				new Compiler().run("tmp/abc.ts", "tmp/out.cas");
				time = -System.currentTimeMillis();
				CaslSimulator.run("tmp/out.cas", "tmp/out.ans");
				time += System.currentTimeMillis();

			}

			if(i < 10) {
				new Lexer().run("data/pas/normal0" + i +".pas", "tmp/abc.ts");
				new Compiler().run("tmp/abc.ts", "tmp/out0"+ i +".cas");
			} else {
				new Lexer().run("data/pas/normal" + i +".pas", "tmp/abc.ts");
				new Compiler().run("tmp/abc.ts", "tmp/out"+ i +".cas");
			}

		}
		//*/

		///*
		if(false) { //debug
			new Lexer().run("data/pas/abc.pas", "tmp/abc.ts");
			new Compiler().run("tmp/abc.ts", "tmp/out.cas");
		} else {
			new Compiler().run("data/ts/normal05.ts", "tmp/out.cas");
		}
		//*/

		// 上記casを，CASLアセンブラ & COMETシミュレータで実行する
		time -= System.currentTimeMillis();

		CaslSimulator.run("tmp/out.cas", "tmp/out.ans");
		time += System.currentTimeMillis();

		System.out.println(time);
	}

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
	List<String []> splittedBuffer; // for input
	List<String> variableQueue; //for manage new declared variable
	int index = 0; //location of splittedBuffer
	Error errorCode; //set errorCode if an error occurred
	String[] st;
	int maxDepth = 0; //foresighted depth used for writing error line
	String constStr = ""; // now constStr (set by constant())
	int constNum = 0; // now constNumber (set by constant())
	int integerNum = 0; // now constNumber (set by integer())
	int constStrIndex = 0; //num of constStr for DC
	int prevVariableCnt = 0;
	int variableCnt = 0; //num of variable


	int subIndex = 0; // num of subProc
	int LoopIndex = 0;// num of loop
	Map <String, Integer> subProgramMap; //subproc name -> named value(Integer)
	Map<String,Map<String, String>> nameSpaces; //scope -> variable name ->  type
	Map<String,Map<String, Integer>> caslNameSpaces; //scope -> variable name -> named value(Integer)
	int libFlag = 0; // to write library (set)

	String tailProgram = ""; //to make end of the casl program

	outPutBuffer outPutBuf; // Dequeue, Buffer for write

	protected enum Error {
		NULL,
		PARSE,
		CHECK,
	};

	public void run(final String inputFileName, final String outputFileName)  {
		errorCode = Error.NULL; 		
		splittedBuffer = new ArrayList<String []>();
		variableQueue = new ArrayList<String>();
		nameSpaces = new HashMap<String,Map<String, String>  >() {
			private static final long serialVersionUID = 1L;
			{
				put("_", new HashMap<String, String>()); //main name caution
			}
		};

		caslNameSpaces = new HashMap<String,Map<String, Integer>  >(){
			private static final long serialVersionUID = 1L;
			{
				put("_", new HashMap<String, Integer>());
			}
		};

		subProgramMap = new HashMap<String, Integer>();
		nowProgramName = "_";
		constStr = "";

		outPutBuf = new outPutBuffer();

		mnemonicOneLine init = new mnemonicOneLine("CASL  START  BEGIN", true);
		outPutBuf.myPush(init);
		init = new mnemonicOneLine("BEGIN", OPERATOR.LAD,  OPERAND.GR6,
				OPERAND.CONSTADDR, OPERAND.NIL,"0", true );
		outPutBuf.myPush(init);
		init = new mnemonicOneLine("", OPERATOR.LAD, OPERAND.GR7, 
				OPERAND.CONSTADDR, OPERAND.NIL,"LIBBUF", true );
		outPutBuf.myPush(init);

		List<String> buffer;
		try {
			buffer = Files.readAllLines(Paths.get(inputFileName));
		} catch (IOException e) {
			System.err.println("File not found");
			return;
		}

		for(String x: buffer) {
			splittedBuffer.add(x.split("\t"));
		}
		if (parse())  	System.out.println("OK");
		else if(errorCode == Error.PARSE) {
			System.err.println("Syntax error: line " + splittedBuffer.get(maxDepth)[3]);
			return;
		} else if  (errorCode == Error.CHECK) {
			System.err.println("Semantic error: line " + splittedBuffer.get(maxDepth)[3]);
			return;
		}

		libAppend();

		outPutBuf.write(outputFileName);
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

		nowProgramName = "_";

		if(! compoundSentence()) {
			raiseError(Error.PARSE);
			return false;
		}


		mnemonicOneLine tmp = new mnemonicOneLine(" RET",  true );
		outPutBuf.myPush(tmp);

		st = getToken();

		// .
		if(! st[2].equals("42")) {
			raiseError(Error.PARSE);
			return false;
		}

		tailProgram += "VAR  DS  " + variableCnt + "\n"  + "LIBBUF DS 256\n  END\n";
		return true;
	}


	private void appendCONSTCHAR() {
		// TODO Auto-generated method stub
		tailProgram += "CHAR" + constStrIndex + "  DC  " 
				+ "'" + constStr + "'\n";
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
		if(! variableDeclaration("_", true)) {
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
		if(variableName(caller, declare, false).equals("false")) {
			return false;
		}
		for(;;) {
			int save_index = index;
			st = getToken();
			if(st[2].equals("41")) {
				if(variableName(caller, declare, false).equals("false")) {
					return false;
				}
			} else {
				index = save_index;
				break;
			}
		}
		return true;
	}
	//set GR2 address
	protected String variableName(String caller, boolean declare, boolean indexed) {
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
				//load G2 offset address 
				if(indexed) {
					mnemonicOneLine tmp = 
							new mnemonicOneLine("", OPERATOR.LAD, OPERAND.GR2, OPERAND.CONSTADDR, OPERAND.NIL, 
									""+ caslNameSpaces.get(caller).get(st[0])  ,  caller.equals("_"));
					outPutBuf.myPush(tmp);
					// ADD G2 offset start index
					if(caslNameSpaces.get(caller).get("_start_"+st[0]) == null) 
						return "false";
					
					tmp = 
							new mnemonicOneLine("", OPERATOR.LAD, OPERAND.GR2, OPERAND.CONSTADDR, OPERAND.GR2, 
									""+ (1 - caslNameSpaces.get(caller).get("_start_"+st[0]))  ,  caller.equals("_"));
					outPutBuf.myPush(tmp);
				}
				return nameSpaces.get(caller).get(st[0]);
			} else {
				if(nameSpaces.get("_").get(st[0]) != null ) {
					if(indexed) {
						//load G2 offset address 
						mnemonicOneLine tmp = 
								new mnemonicOneLine("", OPERATOR.LAD, OPERAND.GR2, OPERAND.CONSTADDR, OPERAND.NIL, 
										""+ caslNameSpaces.get("_").get(st[0])  ,  caller.equals("_"));

						outPutBuf.myPush(tmp);
						// ADD G2 offset start index
						if(caslNameSpaces.get("_").get("_start_"+st[0]) == null) 
							return "false";
						tmp = 
								new mnemonicOneLine("", OPERATOR.LAD, OPERAND.GR2, OPERAND.CONSTADDR, OPERAND.GR2, 
										""+ (1 - caslNameSpaces.get("_").get("_start_"+st[0]))  ,  caller.equals("_"));
						outPutBuf.myPush(tmp);

					}
					return nameSpaces.get("_").get(st[0]);
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

		if(! standardType(0,0, false)) {
			index = save_index;
			if(! arrayType()) {
				return false;
			} else {
				return true;
			}
		}
		return true;
	}	
	protected boolean standardType(int arrayLen, int arrayStart, boolean isParam) {
		st = getToken();
		for(String nowName: variableQueue) {
			if(arrayLen == 0) { //variable
				//boolean, char, integer
				if(st[2].equals("3")) {
					nameSpaces.get(nowProgramName).put(nowName, "boolean");
					caslNameSpaces.get(nowProgramName).put(nowName, variableCnt++);
				}
				if(st[2].equals("4")) {
					nameSpaces.get(nowProgramName).put(nowName, "char");
					caslNameSpaces.get(nowProgramName).put(nowName, variableCnt++);
				}
				if(st[2].equals("11")) {
					nameSpaces.get(nowProgramName).put(nowName, "integer");
					caslNameSpaces.get(nowProgramName).put(nowName, variableCnt++);
				}
			} else {
				//boolean, char, integer of array
				if(st[2].equals("3")) {
					nameSpaces.get(nowProgramName).put(nowName, "Aboolean");
					caslNameSpaces.get(nowProgramName).put(nowName, variableCnt);
					//caslNameSpaces.get(nowProgramName).put("_len_"+nowName, arrayLen);
					caslNameSpaces.get(nowProgramName).put("_start_"+nowName, arrayStart);
					variableCnt += arrayLen;
				}
				if(st[2].equals("4")) {
					nameSpaces.get(nowProgramName).put(nowName, "Achar");
					caslNameSpaces.get(nowProgramName).put(nowName, variableCnt);
					//caslNameSpaces.get(nowProgramName).put("_len_"+nowName, arrayLen);
					caslNameSpaces.get(nowProgramName).put("_start_"+nowName, arrayStart);
					variableCnt += arrayLen;
				}
				if(st[2].equals("11")) {
					nameSpaces.get(nowProgramName).put(nowName, "Ainteger");
					caslNameSpaces.get(nowProgramName).put(nowName, variableCnt);
					//caslNameSpaces.get(nowProgramName).put("_len_"+nowName, arrayLen);
					caslNameSpaces.get(nowProgramName).put("_start_"+nowName, arrayStart);
					variableCnt += arrayLen;
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
		int arrayLen = 1;
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
		int arrayStart = integerNum;
		arrayLen -= integerNum;
		/*
		if (arrayLen < 1) {
			raiseError(Error.CHECK);
			return false;
		}
		*/
		st = getToken();
		// ..
		if(! st[2].equals("39")) {
			return false;
		}
		if(! indexMaximum()) {
			return false;
		}
		arrayLen += integerNum;
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
		if(! standardType(arrayLen, arrayStart, false)) {
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
		integerNum = checkInt;
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
		mnemonicOneLine tmp = 
				new mnemonicOneLine("", OPERATOR.RET, OPERAND.NIL, OPERAND.NIL, OPERAND.NIL, 
						""  ,  false);
		outPutBuf.myPush(tmp);
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
		mnemonicOneLine tmp = new mnemonicOneLine("", OPERATOR.POP, OPERAND.GR5, OPERAND.NIL, OPERAND.NIL, 
				""  ,  false);
		outPutBuf.myPush(tmp);

		tmp = new mnemonicOneLine("", OPERATOR.ADDA, OPERAND.GR8, OPERAND.GR4, OPERAND.NIL, ""  ,  false);
		outPutBuf.myPush(tmp);

		tmp = new mnemonicOneLine("", OPERATOR.LD, OPERAND.GR3, OPERAND.GR8, OPERAND.NIL, ""  ,  false);
		outPutBuf.myPush(tmp);

		tmp = new mnemonicOneLine("", OPERATOR.LAD, OPERAND.GR0, OPERAND.CONSTADDR, OPERAND.NIL,  "0"  ,  false);
		outPutBuf.myPush(tmp);

		tmp = new mnemonicOneLine("ARG"+ LoopIndex , OPERATOR.CPA, 
				OPERAND.GR4, OPERAND.GR0, OPERAND.NIL,  ""  ,  false);
		outPutBuf.myPush(tmp);

		tmp = new mnemonicOneLine("", OPERATOR.JZE, 
				OPERAND.CONSTADDR, OPERAND.NIL, OPERAND.NIL,  "ARGFIN" + LoopIndex  ,  false);
		outPutBuf.myPush(tmp);

		tmp = new mnemonicOneLine("", OPERATOR.SUBA, 
				OPERAND.GR8, OPERAND.CONSTADDR, OPERAND.NIL,  "=1"   ,  false);
		outPutBuf.myPush(tmp);

		tmp = new mnemonicOneLine("", OPERATOR.POP, OPERAND.GR1, OPERAND.NIL, OPERAND.NIL,  ""   ,  false);
		outPutBuf.myPush(tmp);
		tmp = new mnemonicOneLine("", OPERATOR.SUBA, OPERAND.GR8, OPERAND.CONSTADDR, OPERAND.NIL,  "=1"   ,  false);
		outPutBuf.myPush(tmp);
		tmp = new mnemonicOneLine("", OPERATOR.LAD, OPERAND.GR2, OPERAND.CONSTADDR, OPERAND.NIL, 
				caslNameSpaces.get(nowProgramName).get("__head")  + ""   ,  false);
		outPutBuf.myPush(tmp);
		tmp = new mnemonicOneLine("", OPERATOR.ADDA, OPERAND.GR2, OPERAND.GR0, OPERAND.NIL,  ""   ,  false);
		outPutBuf.myPush(tmp);
		tmp = new mnemonicOneLine("", OPERATOR.ST, OPERAND.GR1, OPERAND.CONSTADDR, OPERAND.GR2,  "VAR"   ,  false);
		outPutBuf.myPush(tmp);
		tmp = new mnemonicOneLine("", OPERATOR.ADDA, OPERAND.GR0, OPERAND.CONSTADDR, OPERAND.NIL,  "=1"   ,  false);
		outPutBuf.myPush(tmp);
		tmp = new mnemonicOneLine("", OPERATOR.JUMP, OPERAND.CONSTADDR, OPERAND.NIL, OPERAND.NIL, "ARG"+LoopIndex, false);
		outPutBuf.myPush(tmp);
		tmp = new mnemonicOneLine("ARGFIN"+ LoopIndex++ , OPERATOR.LD, 
				OPERAND.GR8, OPERAND.GR3, OPERAND.NIL,  ""   ,  false);
		outPutBuf.myPush(tmp);
		tmp = new mnemonicOneLine("" , OPERATOR.PUSH,  OPERAND.CONSTADDR, OPERAND.GR5, OPERAND.NIL,  "0"   ,  false);
		outPutBuf.myPush(tmp);


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
				caslNameSpaces.put(st[0], new HashMap<String ,Integer>());
				caslNameSpaces.get(st[0]).put("__head", variableCnt);
				prevVariableCnt = variableCnt; // update
				nowProgramName = st[0];
				variableQueue = new ArrayList<String>();
				subProgramMap.put(st[0], subIndex++);

				mnemonicOneLine tmp = new mnemonicOneLine("SUBP"+ subProgramMap.get(st[0]), 
						OPERATOR.NOP, OPERAND.NIL, OPERAND.NIL, OPERAND.NIL, ""  ,  false);
				outPutBuf.myPush(tmp);
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
		if(! standardType(0,0, true)) {
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
				if(! standardType(0,0, true)) {
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

	int whileIndexMax = 0; //manage num of while
	int ifIndexMax = 0; //manage num of if
	protected boolean sentence() {
		int save_index = index;
		if(! baseSentence()) {

			index = save_index;

			st = getToken();
			
			int nowWhileIndex = whileIndexMax++;
			//while
			if(st[2].equals("22")) {

				mnemonicOneLine tmp = 
						new mnemonicOneLine("WHL" + nowWhileIndex, OPERATOR.NOP, OPERAND.NIL
								, OPERAND.NIL, OPERAND.NIL, "" ,  nowProgramName.equals("_"));

				outPutBuf.myPush(tmp);

				//expression()  GR2 <- var or const
				if (! expression("").equals("boolean")) {
					raiseError(Error.CHECK);
					return false;
				}

				tmp = 
						new mnemonicOneLine("" , OPERATOR.CPL, OPERAND.GR2
								, OPERAND.CONSTADDR, OPERAND.NIL, "=0" ,  nowProgramName.equals("_"));
				outPutBuf.myPush(tmp);

				tmp = 
						new mnemonicOneLine("" , OPERATOR.JZE, OPERAND.CONSTADDR
								, OPERAND.NIL, OPERAND.NIL, "LP" + nowWhileIndex + "ED1" ,  nowProgramName.equals("_"));
				outPutBuf.myPush(tmp);

				st = getToken();
				//do
				if (! st[2].equals("6")) {
					return false;
				}
				if (! compoundSentence()) {
					return false;
				}

				tmp = new mnemonicOneLine( "", OPERATOR.JUMP, OPERAND.CONSTADDR
						, OPERAND.NIL, OPERAND.NIL, "WHL" + nowWhileIndex ,  nowProgramName.equals("_"));
				outPutBuf.myPush(tmp);
				tmp = new mnemonicOneLine( "LP"+nowWhileIndex+"ED1", OPERATOR.NOP, OPERAND.NIL
						, OPERAND.NIL, OPERAND.NIL, "" ,  nowProgramName.equals("_"));
				outPutBuf.myPush(tmp);

				//if
			} else if( st[2].equals("10")) {
				int nowDepth = ifIndexMax++;
				if(!  expression("").equals("boolean")) {
					raiseError(Error.CHECK);
					return false;
				}

				mnemonicOneLine tmp1 = new mnemonicOneLine( "" , OPERATOR.CPL, OPERAND.GR2
						, OPERAND.CONSTADDR, OPERAND.NIL, "=0",  nowProgramName.equals("_") );
				outPutBuf.myPush(tmp1);
				mnemonicOneLine tmp2 = new mnemonicOneLine( "" , OPERATOR.JZE, OPERAND.CONSTADDR
						, OPERAND.NIL, OPERAND.NIL, "E" + nowDepth ,  nowProgramName.equals("_") );
				outPutBuf.myPush(tmp2);


				st = getToken();
				//then
				if(! st[2].equals("19")) {
					return false;
				}
				if(! compoundSentence()) {
					return false;
				}

				tmp1 = new mnemonicOneLine( "" , OPERATOR.JUMP, OPERAND.CONSTADDR
						, OPERAND.NIL , OPERAND.NIL, "F"+nowDepth,  nowProgramName.equals("_") );
				outPutBuf.myPush(tmp1);
				tmp1 =  new mnemonicOneLine( "E" + nowDepth , OPERATOR.NOP, OPERAND.NIL
						, OPERAND.NIL , OPERAND.NIL, "" ,  nowProgramName.equals("_") );
				outPutBuf.myPush(tmp1);
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

				tmp1 = new mnemonicOneLine( "F"+nowDepth , OPERATOR.NOP, OPERAND.NIL
						, OPERAND.NIL , OPERAND.NIL, "",  nowProgramName.equals("_") );
				outPutBuf.myPush(tmp1);

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
				if(! variableList()) {
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
				mnemonicOneLine tmp = new mnemonicOneLine( "" , OPERATOR.CALL, OPERAND.CONSTADDR
						, OPERAND.NIL , OPERAND.NIL, "WRTLN",  nowProgramName.equals("_") );
				outPutBuf.myPush(tmp);
				libFlag |= 1 << 5; //LIBWRTLN
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

	protected boolean variableList() {
		if(variable(false).equals("false")) {
			return false;
		}
		for(;;) {
			int save_index = index;
			if(st[2].equals("41")) {
				if(variable(false).equals("false")) {
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
		// GR3 <- GR2 
		mnemonicOneLine tmp = new mnemonicOneLine( "" , OPERATOR.LD, OPERAND.GR3
				, OPERAND.GR2 , OPERAND.NIL, "",  nowProgramName.equals("_") );
		outPutBuf.myPush(tmp);

		//expression()  GR2 <- var or const
		if(! expression("").equals(ret)) { //no Name
			raiseError(Error.CHECK);
			return false;
		}

		// VARIABLE <- GR2 
		mnemonicOneLine tmp1 = new mnemonicOneLine( "" , OPERATOR.ST, OPERAND.GR2
				, OPERAND.CONSTADDR , OPERAND.GR3, "VAR",  nowProgramName.equals("_") );
		outPutBuf.myPush(tmp1);
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
		if((ret = variableName(nowProgramName,declare,false)).equals("false")) {
			return "false";
		}
		return ret;
	}

	protected String indexedVariable(boolean declare) {
		String ret;
		if((ret = variableName(nowProgramName,false,true)).equals("false")) {
			return "false";
		}

		///* save
		mnemonicOneLine tmp = new mnemonicOneLine( "" , OPERATOR.PUSH, OPERAND.CONSTADDR
				, OPERAND.GR2 , OPERAND.NIL, "0",  nowProgramName.equals("_") );
		outPutBuf.myPush(tmp);

		//*/
		st = getToken();
		//[
		if(! st[2].equals("35")) {
			///* load
			mnemonicOneLine tmp1 = new mnemonicOneLine( "" , OPERATOR.POP, OPERAND.GR2
					, OPERAND.NIL , OPERAND.NIL, "",  nowProgramName.equals("_") );
			outPutBuf.myPush(tmp1);

			return "false";
		}
		// index() -> expression()  GR2 <- var or const
		if(! index()) {
			return "false";
		}
		///* load
		mnemonicOneLine tmp1 = new mnemonicOneLine( "" , OPERATOR.POP, OPERAND.GR1
				, OPERAND.NIL , OPERAND.NIL, "",  nowProgramName.equals("_") );
		outPutBuf.myPush(tmp1);

		tmp1 = new mnemonicOneLine( "" , OPERATOR.SUBA, OPERAND.GR2
				, OPERAND.CONSTADDR , OPERAND.NIL, "=1",  nowProgramName.equals("_") );
		outPutBuf.myPush(tmp1);
		tmp1 = new mnemonicOneLine( "" , OPERATOR.ADDA, OPERAND.GR2
				, OPERAND.GR1 , OPERAND.NIL, "",  nowProgramName.equals("_") );
		outPutBuf.myPush(tmp1);

		//*/
		st = getToken();
		//]
		if(! st[2].equals("36")) {
			return "false";
		}
		return ret.replace("A",	"");
	}

	protected boolean index() {
		//expression()  GR2 <- var or const
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

		mnemonicOneLine tmp1 = new mnemonicOneLine( "" , OPERATOR.PUSH, OPERAND.CONSTADDR
				, OPERAND.GR1 , OPERAND.NIL, "0",  nowProgramName.equals("_") );
		outPutBuf.myPush(tmp1);
		tmp1 = new mnemonicOneLine( "" , OPERATOR.PUSH, OPERAND.CONSTADDR
				, OPERAND.GR2 , OPERAND.NIL, "0",  nowProgramName.equals("_") );
		outPutBuf.myPush(tmp1);
		tmp1 = new mnemonicOneLine( "" , OPERATOR.PUSH, OPERAND.CONSTADDR
				, OPERAND.GR3 , OPERAND.NIL, "0",  nowProgramName.equals("_") );
		outPutBuf.myPush(tmp1);

		for(int i = prevVariableCnt; i < variableCnt; i++) {
			tmp1 = new mnemonicOneLine( "" , OPERATOR.LAD, OPERAND.GR2, OPERAND.CONSTADDR , 
					OPERAND.NIL , ""+i ,  nowProgramName.equals("_") ); 
			outPutBuf.myPush(tmp1);
			// GR1 = variable
			tmp1 = new mnemonicOneLine( "" , OPERATOR.LD, OPERAND.GR1, OPERAND.CONSTADDR , 
					OPERAND.GR2 , "VAR" ,  nowProgramName.equals("_") ); 
			outPutBuf.myPush(tmp1);
			tmp1 = new mnemonicOneLine( "" , OPERATOR.PUSH, OPERAND.CONSTADDR
					, OPERAND.GR1 , OPERAND.NIL, "0",  nowProgramName.equals("_") );
			outPutBuf.myPush(tmp1);
		}


		tmp1 = new mnemonicOneLine( "" , OPERATOR.LAD, OPERAND.GR4
				, OPERAND.CONSTADDR , OPERAND.NIL, "0",  nowProgramName.equals("_") );
		outPutBuf.myPush(tmp1);

		int save_index = index;
		st = getToken();
		//(
		if(st[2].equals("33")) {
			//set GR4 and push
			if(! expressionList(retName)) { // push parameters
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
		tmp1 = new mnemonicOneLine( "" , OPERATOR.CALL, OPERAND.CONSTADDR, OPERAND.NIL , 
				OPERAND.NIL, "SUBP" + subProgramMap.get(retName),  nowProgramName.equals("_") );
		outPutBuf.myPush(tmp1);
		
		for(int i = variableCnt - 1; i >= prevVariableCnt; i--) {
			tmp1 = new mnemonicOneLine( "" , OPERATOR.POP, OPERAND.GR1
					, OPERAND.NIL , OPERAND.NIL, "",  nowProgramName.equals("_") );
			outPutBuf.myPush(tmp1);
			tmp1 = new mnemonicOneLine( "" , OPERATOR.LAD, OPERAND.GR2, OPERAND.CONSTADDR , 
					OPERAND.NIL , ""+i ,  nowProgramName.equals("_") ); 
			outPutBuf.myPush(tmp1);
			tmp1 = new mnemonicOneLine( "" , OPERATOR.ST, OPERAND.GR1, OPERAND.CONSTADDR , 
					OPERAND.GR2 , "VAR" ,  nowProgramName.equals("_") ); 
			outPutBuf.myPush(tmp1);
		}
		
		tmp1 = new mnemonicOneLine( "" , OPERATOR.POP, OPERAND.GR3
				, OPERAND.NIL , OPERAND.NIL, "",  nowProgramName.equals("_") );
		outPutBuf.myPush(tmp1);
		tmp1 = new mnemonicOneLine( "" , OPERATOR.POP, OPERAND.GR2
				, OPERAND.NIL , OPERAND.NIL, "",  nowProgramName.equals("_") );
		outPutBuf.myPush(tmp1);
		tmp1 = new mnemonicOneLine( "" , OPERATOR.POP, OPERAND.GR1
				, OPERAND.NIL , OPERAND.NIL, "",  nowProgramName.equals("_") );
		outPutBuf.myPush(tmp1);


		return true;
	}

	protected boolean expressionList(String procedureName) {
		String ret;
		int numParameter = 0;

		if((ret = expression(procedureName)).equals("false")) {
			return false;
		} else if(ret.equals("str") && procedureName.equals("writeln")){
			//make sure GR2 = const char(STR) address
			// GR1 = length(STR)
			mnemonicOneLine tmp1 = new mnemonicOneLine( "" , OPERATOR.LAD, OPERAND.GR1, OPERAND.CONSTADDR , 
					OPERAND.NIL, constStr.length() + "",  nowProgramName.equals("_") );
			outPutBuf.myPush(tmp1);
			tmp1 = new mnemonicOneLine( "" , OPERATOR.LAD, OPERAND.GR2, OPERAND.CONSTADDR , 
					OPERAND.NIL, "CHAR" + constStrIndex,  nowProgramName.equals("_") );
			outPutBuf.myPush(tmp1);
			tmp1 = new mnemonicOneLine( "" , OPERATOR.CALL, OPERAND.CONSTADDR, OPERAND.NIL , 
					OPERAND.NIL, "WRTSTR",  nowProgramName.equals("_") );
			outPutBuf.myPush(tmp1);
			constStrIndex++;
			libFlag |= 1 << 4; //LIBWRTSTR
		} else if(ret.equals("char") && procedureName.equals("writeln")){
			//make sure GR2 = variant char
			mnemonicOneLine tmp1 = new mnemonicOneLine( "" , OPERATOR.CALL, OPERAND.CONSTADDR, OPERAND.NIL , 
					OPERAND.NIL, "WRTCH",  nowProgramName.equals("_") );
			outPutBuf.myPush(tmp1);
			libFlag |= 1 << 3; //LIBWRTCH
		} else if(ret.equals("integer") && procedureName.equals("writeln")){
			//caution must variant: integer
			//make sure GR2 = variant or const int
			mnemonicOneLine tmp1 = new mnemonicOneLine( "" , OPERATOR.CALL, OPERAND.CONSTADDR, OPERAND.NIL , 
					OPERAND.NIL, "WRTINT",  nowProgramName.equals("_") );
			outPutBuf.myPush(tmp1);
			libFlag |= 1 << 2; //LIBWRTINT
		} else {
			mnemonicOneLine tmp1 = new mnemonicOneLine( "" , OPERATOR.PUSH, OPERAND.CONSTADDR, OPERAND.GR2 , 
					OPERAND.NIL, "0",  nowProgramName.equals("_") );
			outPutBuf.myPush(tmp1);
			numParameter++;
		}

		for(;;) {
			int save_index = index;
			st = getToken();
			// ,
			if(st[2].equals("41")) {
				if((ret = expression(procedureName)).equals("false")) {
					return false;
				} else if(ret.equals("str") && procedureName.equals("writeln")){
					//make sure GR2 = const char(STR) address
					// GR1 = length(STR)
					mnemonicOneLine tmp1 = new mnemonicOneLine( "" , OPERATOR.LAD, OPERAND.GR1, OPERAND.CONSTADDR , 
							OPERAND.NIL, constStr.length()+"",  nowProgramName.equals("_") );
					outPutBuf.myPush(tmp1);
					tmp1 = new mnemonicOneLine( "" , OPERATOR.LAD, OPERAND.GR2, OPERAND.CONSTADDR , 
							OPERAND.NIL, "CHAR" + constStrIndex,  nowProgramName.equals("_") );
					outPutBuf.myPush(tmp1);
					tmp1 = new mnemonicOneLine( "" , OPERATOR.CALL, OPERAND.CONSTADDR, OPERAND.NIL , 
							OPERAND.NIL, "WRTSTR" ,  nowProgramName.equals("_") );
					outPutBuf.myPush(tmp1);
					constStrIndex++;
					libFlag |= 1 << 4; //LIBWRTSTR
				} else if(ret.equals("char") && procedureName.equals("writeln")){
					//make sure GR2 = variant char
					mnemonicOneLine tmp1 = new mnemonicOneLine( "" , OPERATOR.CALL, OPERAND.CONSTADDR, OPERAND.NIL , 
							OPERAND.NIL, "WRTCH" ,  nowProgramName.equals("_") );
					outPutBuf.myPush(tmp1);
					libFlag |= 1 << 3; //LIBWRTCH
				} else if(ret.equals("integer") && procedureName.equals("writeln")){
					//caution must variant: integer
					//make sure GR2 = variant or const int
					mnemonicOneLine tmp1 = new mnemonicOneLine( "" , OPERATOR.CALL, OPERAND.CONSTADDR, OPERAND.NIL , 
							OPERAND.NIL, "WRTINT" ,  nowProgramName.equals("_") );
					libFlag |= 1 << 2; //LIBWRTINT
					outPutBuf.myPush(tmp1);

				}  else {
					mnemonicOneLine tmp1 = new mnemonicOneLine( "" , OPERATOR.PUSH, OPERAND.CONSTADDR, OPERAND.GR2 , 
							OPERAND.NIL, "0" ,  nowProgramName.equals("_") );
					outPutBuf.myPush(tmp1);
					numParameter++;
				}
			} else {
				index = save_index;
				break;
			}
		}
		mnemonicOneLine tmp = new mnemonicOneLine( "" , OPERATOR.LAD, OPERAND.GR4, OPERAND.CONSTADDR , 
				OPERAND.NIL, numParameter + "" ,  nowProgramName.equals("_") );
		outPutBuf.myPush(tmp);

		return true;
	}
	//expression()  GR2 <- var or const
	protected String expression(String procedureName) {
		String ret1, ret2;
		// simpleExpression GR1 <- var or const
		if((ret1 = simpleExpression(procedureName)).equals("false")) {
			raiseError(Error.PARSE);
			return "false";
		}

		// save 
		mnemonicOneLine tmp1 = new mnemonicOneLine( "" , OPERATOR.PUSH, OPERAND.CONSTADDR, OPERAND.GR1 , 
				OPERAND.NIL, "0" ,  nowProgramName.equals("_") );
		outPutBuf.myPush(tmp1);


		int save_index = index;

		String ret;
		if(! (ret = relationOperator()).equals("false")) {
			// simpleExpression GR1 <- var or const
			if((ret2 = simpleExpression(procedureName)).equals("false")) {
				raiseError(Error.PARSE);
				return "false";
			}
			if (! ret1.equals(ret2)) {
				raiseError(Error.CHECK);
				return "false";
			}
			// load
			tmp1 = new mnemonicOneLine( "" , OPERATOR.POP, OPERAND.GR2, OPERAND.NIL , 
					OPERAND.NIL, "" ,  nowProgramName.equals("_") );
			outPutBuf.myPush(tmp1);


			//extend library EQUAL LAD GR2, 1 (if true)
			if(ret.equals("=")) {
				tmp1 = new mnemonicOneLine( "" , OPERATOR.CALL, OPERAND.CONSTADDR, OPERAND.NIL , 
						OPERAND.NIL, "EQUAL" ,  nowProgramName.equals("_") );
				outPutBuf.myPush(tmp1);
				libFlag |= 1 << 6; //LIBEQUAL
			}
			if(ret.equals("<>")) {
				tmp1 = new mnemonicOneLine( "" , OPERATOR.CALL, OPERAND.CONSTADDR, OPERAND.NIL , 
						OPERAND.NIL, "EQUAL" ,  nowProgramName.equals("_") );
				outPutBuf.myPush(tmp1);
				tmp1 = new mnemonicOneLine( "" , OPERATOR.XOR, OPERAND.GR2 , OPERAND.CONSTADDR , 
						OPERAND.NIL, "=1" ,  nowProgramName.equals("_") );
				outPutBuf.myPush(tmp1);
				libFlag |= 1 << 6; //LIBEQUAL
			}
			if(ret.equals("<")) {
				tmp1 = new mnemonicOneLine( "" , OPERATOR.SUBA, OPERAND.GR2, OPERAND.GR1 , 
						OPERAND.NIL, "" ,  nowProgramName.equals("_") );
				outPutBuf.myPush(tmp1);
				tmp1 = new mnemonicOneLine( "" , OPERATOR.SRL, OPERAND.GR2, OPERAND.CONSTADDR , 
						OPERAND.NIL, "15" ,  nowProgramName.equals("_") );
				outPutBuf.myPush(tmp1);
			}
			if(ret.equals("<=")) {
				tmp1 = new mnemonicOneLine( "" , OPERATOR.ADDA, OPERAND.GR1 , OPERAND.CONSTADDR , 
						OPERAND.NIL, "=1" ,  nowProgramName.equals("_") );
				outPutBuf.myPush(tmp1);
				tmp1 = new mnemonicOneLine( "" , OPERATOR.SUBA, OPERAND.GR2, OPERAND.GR1 , 
						OPERAND.NIL, "" ,  nowProgramName.equals("_") );
				outPutBuf.myPush(tmp1);
				tmp1 = new mnemonicOneLine( "" , OPERATOR.SRL, OPERAND.GR2, OPERAND.CONSTADDR , 
						OPERAND.NIL, "15" ,  nowProgramName.equals("_") );
				outPutBuf.myPush(tmp1);
			}
			if(ret.equals(">")) {
				tmp1 = new mnemonicOneLine( "" , OPERATOR.SUBA, OPERAND.GR1, OPERAND.GR2 , 
						OPERAND.NIL, "" ,  nowProgramName.equals("_") );
				outPutBuf.myPush(tmp1);
				tmp1 = new mnemonicOneLine( "" , OPERATOR.SRL, OPERAND.GR1, OPERAND.CONSTADDR , 
						OPERAND.NIL, "15" ,  nowProgramName.equals("_") );
				outPutBuf.myPush(tmp1);
				tmp1 = new mnemonicOneLine( "" , OPERATOR.LD, OPERAND.GR2, OPERAND.GR1 , 
						OPERAND.NIL, "" ,  nowProgramName.equals("_") );
				outPutBuf.myPush(tmp1);

			}
			if(ret.equals(">=")) {
				tmp1 = new mnemonicOneLine( "" , OPERATOR.ADDA, OPERAND.GR2, OPERAND.CONSTADDR , 
						OPERAND.NIL, "=1" ,  nowProgramName.equals("_") );
				outPutBuf.myPush(tmp1);
				tmp1 = new mnemonicOneLine( "" , OPERATOR.SUBA, OPERAND.GR1, OPERAND.GR2 , 
						OPERAND.NIL, "" ,  nowProgramName.equals("_") );
				outPutBuf.myPush(tmp1);
				tmp1 = new mnemonicOneLine( "" , OPERATOR.SRL, OPERAND.GR1, OPERAND.CONSTADDR , 
						OPERAND.NIL, "15" ,  nowProgramName.equals("_") );
				outPutBuf.myPush(tmp1);
				tmp1 = new mnemonicOneLine( "" , OPERATOR.LD, OPERAND.GR2, OPERAND.GR1 , 
						OPERAND.NIL, "" ,  nowProgramName.equals("_") );
				outPutBuf.myPush(tmp1);
			}
			return "boolean";
		} else {
			// load
			tmp1 = new mnemonicOneLine( "" , OPERATOR.POP, OPERAND.GR2, OPERAND.NIL , 
					OPERAND.NIL, "" ,  nowProgramName.equals("_") );
			outPutBuf.myPush(tmp1);

			index = save_index;
		}
		return ret1;
	}

	// GR1 <- var or const
	protected String simpleExpression(String procedureName) {
		int save_index = index;
		st = getToken();
		String ret1, ret2;
		// + or -
		String save = st[2];
		if(st[2].equals("30") || st[2].equals("31")) {
			;
		} else {
			index = save_index;
		}
		//term() GR2 <- var or const
		if((ret1 = term(procedureName)).equals("false") ) {
			return "false";
		}

		if (save.equals("31")) {

			mnemonicOneLine tmp1 = new mnemonicOneLine( "" , OPERATOR.XOR, OPERAND.GR2, OPERAND.CONSTADDR , 
					OPERAND.NIL, "=#FFFF" ,  nowProgramName.equals("_") );
			outPutBuf.myPush(tmp1);
			tmp1 = new mnemonicOneLine( "" , OPERATOR.LAD, OPERAND.GR2, OPERAND.CONSTADDR , 
					OPERAND.GR2, "1" ,  nowProgramName.equals("_") );
			outPutBuf.myPush(tmp1);
		}

		// save GR2 

		mnemonicOneLine tmp1 = new mnemonicOneLine( "" , OPERATOR.PUSH, OPERAND.CONSTADDR, OPERAND.GR2 , 
				OPERAND.NIL, "0" ,  nowProgramName.equals("_") );
		outPutBuf.myPush(tmp1);

		for(;;) {
			int save_index2 = index;
			String ret;
			if(! (ret = additiveperator()).equals("false")) {
				//term() GR2 <- var or const
				if((ret2 = term(procedureName)).equals("false")) {
					return "false";
				}
				if (! ret1.equals(ret2)) {
					raiseError(Error.CHECK);
					return "false";
				}
				// load to GR1
				tmp1 = new mnemonicOneLine( "" , OPERATOR.POP, OPERAND.GR1, OPERAND.NIL , 
						OPERAND.NIL, "" ,  nowProgramName.equals("_") );
				outPutBuf.myPush(tmp1);

				if(ret.equals("+")) {
					tmp1 = new mnemonicOneLine( "" , OPERATOR.ADDA, OPERAND.GR1, OPERAND.GR2 , 
							OPERAND.NIL, "" ,  nowProgramName.equals("_") );
					outPutBuf.myPush(tmp1);

				} else if(ret.equals("-")) {
					tmp1 = new mnemonicOneLine( "" , OPERATOR.SUBA, OPERAND.GR1, OPERAND.GR2 , 
							OPERAND.NIL, "" ,  nowProgramName.equals("_") );
					outPutBuf.myPush(tmp1);

				} else if(ret.equals("or")) {
					tmp1 = new mnemonicOneLine( "" , OPERATOR.OR, OPERAND.GR1, OPERAND.GR2 , 
							OPERAND.NIL, "" ,  nowProgramName.equals("_") );
					outPutBuf.myPush(tmp1);

				}
				// save GR2
				tmp1 = new mnemonicOneLine( "" , OPERATOR.PUSH, OPERAND.CONSTADDR, OPERAND.GR1 , 
						OPERAND.NIL, "0" ,  nowProgramName.equals("_") );
				outPutBuf.myPush(tmp1);
			} else {
				// load to GR1
				tmp1 = new mnemonicOneLine( "" , OPERATOR.POP, OPERAND.GR1, OPERAND.NIL , 
						OPERAND.NIL, "" ,  nowProgramName.equals("_") );
				outPutBuf.myPush(tmp1);

				index = save_index2;
				break;
			}
		}
		return ret1;
	}

	//term() GR2 <- var or const
	protected String term(String procedureName) {
		String ret1, ret2;
		//divisor() load GR1 <- var or const
		if((ret1 = divisor(procedureName)).equals("false")) {
			return "false";
		}


		// save
		mnemonicOneLine tmp1 = new mnemonicOneLine( "" , OPERATOR.PUSH, OPERAND.CONSTADDR, OPERAND.GR1 , 
				OPERAND.NIL, "0" ,  nowProgramName.equals("_") );
		outPutBuf.myPush(tmp1);

		for(;;) {
			int save_index = index;
			String ret;
			if (! (ret = multiplicativeOperator()).equals("false")) {
				//divisor() load GR1 <- var or const
				if ((ret2 = divisor(procedureName)).equals("false")) {
					return "false";
				}
				if (!ret1.equals(ret2)) {
					raiseError(Error.CHECK);
					return "false";
				}
				// load to GR2
				mnemonicOneLine tmp = new mnemonicOneLine( "" , OPERATOR.POP, OPERAND.GR2, OPERAND.NIL , 
						OPERAND.NIL, "" ,  nowProgramName.equals("_") );
				outPutBuf.myPush(tmp);


				//mult GR2 <- GR2 * GR1
				if (ret.equals("*")) {
					tmp = new mnemonicOneLine( "" , OPERATOR.CALL, OPERAND.CONSTADDR, OPERAND.NIL , 
							OPERAND.NIL, "MULT" ,  nowProgramName.equals("_") );
					libFlag |= (1 << 0); // LIBMULT
					outPutBuf.myPush(tmp);

					//DIV GR1 / GR2 = GR2 ... GR1 
				} else if(ret.equals("/")) {
					tmp = new mnemonicOneLine( "" , OPERATOR.PUSH, OPERAND.CONSTADDR, OPERAND.GR1 , 
							OPERAND.NIL, "0" ,  nowProgramName.equals("_") );
					outPutBuf.myPush(tmp);
					tmp = new mnemonicOneLine( "" , OPERATOR.LD, OPERAND.GR1, OPERAND.GR2 , 
							OPERAND.NIL, "" ,  nowProgramName.equals("_") );
					outPutBuf.myPush(tmp);
					tmp = new mnemonicOneLine( "" , OPERATOR.POP, OPERAND.GR2, OPERAND.NIL , 
							OPERAND.NIL, "" ,  nowProgramName.equals("_") );
					outPutBuf.myPush(tmp);
					tmp = new mnemonicOneLine( "" , OPERATOR.CALL, OPERAND.CONSTADDR, OPERAND.NIL , 
							OPERAND.NIL, "DIV" ,  nowProgramName.equals("_") );
					outPutBuf.myPush(tmp);
					libFlag |= (1 << 1); // LIBDIV

				} else if(ret.equals("mod")) {
					tmp = new mnemonicOneLine( "" , OPERATOR.PUSH, OPERAND.CONSTADDR, OPERAND.GR1 , 
							OPERAND.NIL, "0" ,  nowProgramName.equals("_") );
					outPutBuf.myPush(tmp);
					tmp = new mnemonicOneLine( "" , OPERATOR.LD, OPERAND.GR1, OPERAND.GR2 , 
							OPERAND.NIL, "" ,  nowProgramName.equals("_") );
					outPutBuf.myPush(tmp);
					tmp = new mnemonicOneLine( "" , OPERATOR.POP, OPERAND.GR2, OPERAND.NIL , 
							OPERAND.NIL, "" ,  nowProgramName.equals("_") );
					outPutBuf.myPush(tmp);
					tmp = new mnemonicOneLine( "" , OPERATOR.CALL, OPERAND.CONSTADDR, OPERAND.NIL , 
							OPERAND.NIL, "DIV" ,  nowProgramName.equals("_") );
					outPutBuf.myPush(tmp);
					tmp = new mnemonicOneLine( "" , OPERATOR.LD, OPERAND.GR2, OPERAND.GR1 , 
							OPERAND.NIL, "" ,  nowProgramName.equals("_") );
					outPutBuf.myPush(tmp);
					libFlag |= (1 << 1); // LIBDIV
				} else if(ret.equals("and")) {
					tmp = new mnemonicOneLine( "" , OPERATOR.AND, OPERAND.GR2, OPERAND.GR1 , 
							OPERAND.NIL, "" ,  nowProgramName.equals("_") );
					outPutBuf.myPush(tmp);
				}
				// save GR2 
				tmp = new mnemonicOneLine( "" , OPERATOR.PUSH, OPERAND.CONSTADDR, OPERAND.GR2 , 
						OPERAND.NIL, "0" ,  nowProgramName.equals("_") );
				outPutBuf.myPush(tmp);

			} else {
				// load to GR2
				mnemonicOneLine tmp = new mnemonicOneLine( "" , OPERATOR.POP, OPERAND.GR2, OPERAND.NIL , 
						OPERAND.NIL, "" ,  nowProgramName.equals("_") );
				outPutBuf.myPush(tmp);
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
					mnemonicOneLine tmp = new mnemonicOneLine( "" , OPERATOR.LD, OPERAND.GR1, OPERAND.GR2 , 
							OPERAND.NIL, "" ,  nowProgramName.equals("_") );
					outPutBuf.myPush(tmp);

					return ret;
					//not
				} else if(st[2].equals("13")) {
					if(! (ret = divisor(procedureName)).equals("boolean")) {
						return "false";
					} else {
						mnemonicOneLine tmp = new mnemonicOneLine( "" , OPERATOR.XOR, OPERAND.GR1, OPERAND.CONSTADDR , 
								OPERAND.NIL, "=1" ,  nowProgramName.equals("_") );
						outPutBuf.myPush(tmp);

						return "boolean";
					}
				} else {
					return "false";
				}
			} else {
				//constant
				if (procedureName.equals("writeln") && ret.equals("char")) {
					appendCONSTCHAR();
					return "str";
				} else {
					// GR1 = constant
					mnemonicOneLine tmp = new mnemonicOneLine( "" , OPERATOR.LAD, OPERAND.GR1, OPERAND.CONSTADDR , 
							OPERAND.NIL, constNum+"" ,  nowProgramName.equals("_") );
					outPutBuf.myPush(tmp);
					return ret;
				}
			}
		} else  {
			// GR1 = variable
			mnemonicOneLine tmp1 = new mnemonicOneLine( "" , OPERATOR.LD, OPERAND.GR1, OPERAND.CONSTADDR , 
					OPERAND.GR2 , "VAR" ,  nowProgramName.equals("_") ); // main
			outPutBuf.myPush(tmp1);
			return varRet;
			//variable
		}
	}

	protected String constant() {
		st = getToken();
		// unsigned int
		if (st[2].equals("44")) {
			constNum = Integer.parseInt(st[0]);
			return "integer";
			//string
		} else if(st[2].equals("45")) {
			constStr = st[0].replace("'", ""); 
			constNum = constStr.charAt(0); 
			return "char"; //or str
			// false
		} else if(st[2].equals("9")) {
			constNum = 0;
			return "boolean";
			// true
		} else if(st[2].equals("20")) {
			constNum = 1;
			return "boolean";
		}
		return "false";
	}

	protected String multiplicativeOperator() {
		st = getToken();
		// "*" | "/" | "div" | "mod" | "and".
		if (st[2].equals("32")) {
			return "*";
		}
		if (st[2].equals("5")) {
			return "/";
		}

		if (st[2].equals("12")) {
			return "mod";
		}
		if (st[2].equals("0")) {
			return "and";
		}
		return "false";
	}

	protected String relationOperator() {
		st = getToken();
		// = or <> or < or <= or > or >=
		if(st[2].equals("24")) {
			return "=";
		}
		if(st[2].equals("25")) {
			return "<>";
		}
		if(st[2].equals("26")) {
			return "<";
		}
		if(st[2].equals("27")) {
			return "<=";
		}
		if(st[2].equals("29")) {
			return ">";
		}
		if(st[2].equals("28")) {
			return ">=";
		}
		return "false";
	}

	protected String additiveperator() {
		st = getToken();
		// + || - || or
		if(st[2].equals("30")) {
			return "+";
		}
		if(st[2].equals("31")) {
			return "-";
		}
		if(st[2].equals("15")) {
			return "or";
		}		
		return "false";
	}

	protected boolean parse() {
		return program();
	}

	protected String[] getToken() {
		maxDepth = maxDepth < index ? index : maxDepth; // save maximum depth of parse
		return splittedBuffer.get(index++);
	}

	protected void raiseError(Error e) {
		if (errorCode == Error.NULL) errorCode = e;
	}

	//------------------------for output-------------------------------------------------------------------

	protected class outPutBuffer extends LinkedList<mnemonicOneLine> {
		private static final long serialVersionUID = 1L;
		LinkedList<mnemonicOneLine> mainProgram = new LinkedList<mnemonicOneLine>();
		LinkedList<mnemonicOneLine> subProgram = new LinkedList<mnemonicOneLine>();

		protected void myPush(mnemonicOneLine newLine) {
			LinkedList <mnemonicOneLine> nowProgram = (newLine.isMain) ? mainProgram : subProgram;
			if(nowProgram.size() == 0) {
				nowProgram.push(newLine);
				return;
			}
			mnemonicOneLine last = nowProgram.removeLast();

			if(! last.label.equals(newLine.label)) {
				nowProgram.addLast(last);
				nowProgram.addLast(newLine);
				return;
			}

			if(newLine.op == OPERATOR.POP) {
				if(last.op == OPERATOR.PUSH) {
					newLine.op = OPERATOR.LD;
					// newLine.arg1 = newLine.arg1;
					newLine.arg2 = last.arg2; // PUSH 0, GRX
					if(newLine.arg1 != newLine.arg2) {
						outPutBuf.myPush(newLine);
					} else {
						;
					}
					return;
				} 
			} else if (newLine.op == OPERATOR.LD) {
				if(last.op == OPERATOR.LD) {
					//System.out.println(OpeTable.get(newLine.op));
					//System.out.println(ArgTable.get(newLine.arg1));
					//System.out.println(ArgTable.get(newLine.arg2));
					//System.out.println(ArgTable.get(newLine.arg3));
					if((last.arg1 == newLine.arg2) && (newLine.arg1 == last.arg2) ) {
						outPutBuf.myPush(last);
						return;
					} else if ((last.arg1 == newLine.arg1) && (last.arg2 == newLine.arg2)) {
						outPutBuf.myPush(last);
						return;
					} else if ((last.arg1 == newLine.arg1) && (last.arg2 != newLine.arg2)) {
						outPutBuf.myPush(newLine);
						return;

					}
				} 
			} else if (newLine.op == OPERATOR.ST) {
				mnemonicOneLine last2 = nowProgram.getLast();
				if(last.op == OPERATOR.LD && last.arg3 == OPERAND.NIL 
						&& last2.op == OPERATOR.LAD && last2.arg3 == OPERAND.NIL) {
					if(last2.arg1 == last.arg2) {
						newLine.arg1 = last2.arg1;
						outPutBuf.myPush(newLine);
						return;
					}
				}
			} else if (newLine.op == OPERATOR.SUBA && newLine.arg2 == OPERAND.CONSTADDR) {
				if(last.op == OPERATOR.POP) {
					mnemonicOneLine last2 = nowProgram.removeLast();
					if(last2.op == OPERATOR.LD ) {
						if(last2.arg1 == newLine.arg1) {
							last2.op = OPERATOR.LAD;
							last2.arg3 = last2.arg2;
							last2.arg2 = OPERAND.CONSTADDR;
							last2.constAddr = newLine.constAddr.replace("=", "-");
							outPutBuf.myPush(last2);
							nowProgram.addLast(last);
							return;
						}
					}
					nowProgram.addLast(last2);
				}
			} else if (newLine.op == OPERATOR.CALL && last.op == OPERATOR.LD &&
					last.arg2 != OPERAND.CONSTADDR && last.arg3 == OPERAND.NIL) {
				mnemonicOneLine last2 = nowProgram.removeLast();
				if(last2.op == OPERATOR.LD && last2.arg1 == last.arg2) {
					last2.arg1 = last.arg1;
					outPutBuf.myPush(last2);
					outPutBuf.myPush(newLine);
					return;
				}
				nowProgram.addLast(last2);
			} else if (newLine.op == OPERATOR.JZE ) { //caution: have to confirm CPA "=0"
				if(last.op == OPERATOR.CPL ) {
					mnemonicOneLine last2 = nowProgram.removeLast();
					if(last2.op == OPERATOR.CALL && last2.constAddr.equals("EQUAL")) {
						last.arg2 = OPERAND.GR1;
						last.constAddr = ""; 
						newLine.op = OPERATOR.JNZ;
						nowProgram.addLast(last);
						nowProgram.addLast(newLine);
						return;
					}
					outPutBuf.myPush(last2);
				}
			}
			nowProgram.addLast(last);
			nowProgram.addLast(newLine);
			return;
		}

		protected String getArgument(OPERAND ope, String constAddr) {
			if(ope == OPERAND.NIL) return "";
			else if(ope == OPERAND.CONSTADDR) return constAddr + ", ";
			else return ArgTable.get(ope) + ", ";
		}


		protected void write(String outputFileName) {
			try{
				Files.deleteIfExists(Paths.get(outputFileName));
				Files.writeString(Paths.get(outputFileName), "", StandardOpenOption.CREATE);
			}catch(IOException e){
				System.out.println(e);
			}

			try {
				///*
				for(mnemonicOneLine line: mainProgram) {
					String x = (line.label + OpeTable.get(line.op) +  getArgument(line.arg1, line.constAddr) 
					+ getArgument(line.arg2, line.constAddr) +  getArgument(line.arg3, line.constAddr));

					Files.writeString(Paths.get(outputFileName), x.substring(0, x.length()-2) + "\n", StandardOpenOption.APPEND);
				}
				for(mnemonicOneLine line: subProgram) {
					String x = (line.label +  OpeTable.get(line.op) +  getArgument(line.arg1, line.constAddr) 
					+ getArgument(line.arg2, line.constAddr) +  getArgument(line.arg3, line.constAddr));

					Files.writeString(Paths.get(outputFileName), x.substring(0, x.length()-2) + "\n", StandardOpenOption.APPEND);
				}
				Files.writeString(Paths.get(outputFileName), tailProgram, StandardOpenOption.APPEND);
				//*/
			}catch(IOException e){
				System.out.println(e);
			}
		}

	}


	protected class mnemonicOneLine {
		OPERATOR op;
		OPERAND arg1, arg2, arg3;
		boolean isMain;
		String  label, constAddr;
		mnemonicOneLine(String label, OPERATOR o, OPERAND a1, OPERAND a2, OPERAND a3, String constAddr,boolean isMain) {
			this.label = label;
			this.op = o;
			this.arg1 = a1;
			this.arg2 = a2;
			this.arg3 = a3;
			this.constAddr = constAddr;
			this.isMain = isMain;
		}
		mnemonicOneLine(String str, boolean isMain) {
			this.label = str;
			this.op = OPERATOR.NIL;
			this.arg1 = OPERAND.NIL;
			this.arg2 = OPERAND.NIL;
			this.arg3 = OPERAND.NIL;
			this.constAddr = "";
			this.isMain = isMain;
		}
	}

	protected enum OPERATOR  {
		NIL, LD, ST, LAD, ADDA, ADDL, SUBA, SUBL, AND, OR, XOR, CPA,
		CPL, SLA, SRA, SLL, SRL, JPL, JMI, JNZ, JZE, JOV, JUMP,
		PUSH, POP, CALL, RET, SVC, NOP,
	};

	protected Map<OPERATOR, String> OpeTable = new HashMap<OPERATOR, String>() {
		private static final long serialVersionUID = 1L;
		{
			put(OPERATOR.NIL,  "    "  );
			put(OPERATOR.LD,  "  LD  "  );
			put(OPERATOR.ST,  "  ST  "  );
			put(OPERATOR.LAD,  "  LAD  "  );
			put(OPERATOR.ADDA,  "  ADDA  "  );
			put(OPERATOR.ADDL,  "  ADDL  "  );
			put(OPERATOR.SUBA,  "  SUBA  "  );
			put(OPERATOR.SUBL,  "  SUBL  "  );
			put(OPERATOR.AND,  "  AND  "  );
			put(OPERATOR.OR,  "  OR  "  );
			put(OPERATOR.XOR,  "  XOR  "  );
			put(OPERATOR.CPA,  "  CPA  "  );
			put(OPERATOR.CPL,  "  CPL  "  );
			put(OPERATOR.SLA,  "  SLA  "  );
			put(OPERATOR.SRA,  "  SRA  "  );
			put(OPERATOR.SLL,  "  SLL  "  );
			put(OPERATOR.SRL,  "  SRL  "  );
			put(OPERATOR.JPL,  "  JPL  "  );
			put(OPERATOR.JMI,  "  JMI  "  );
			put(OPERATOR.JNZ,  "  JNZ  "  );
			put(OPERATOR.JZE,  "  JZE  "  );
			put(OPERATOR.JOV,  "  JOV  "  );
			put(OPERATOR.JUMP,  "  JUMP  "  );
			put(OPERATOR.PUSH,  "  PUSH  "  );
			put(OPERATOR.POP,  "  POP  "  );
			put(OPERATOR.CALL,  "  CALL  "  );
			put(OPERATOR.RET,  "  RET  "  );
			put(OPERATOR.SVC,  "  SVC  "  );
			put(OPERATOR.NOP,  "  NOP  "  );
		}
	};


	protected enum OPERAND {
		NIL, GR0, GR1, GR2, GR3, GR4, GR5, GR6, GR7, GR8, CONSTADDR 
	}

	protected Map<OPERAND, String> ArgTable = new HashMap<OPERAND, String>() {
		private static final long serialVersionUID = 1L;
		{
			put(OPERAND.GR0, "GR0");
			put(OPERAND.GR1, "GR1");
			put(OPERAND.GR2, "GR2");
			put(OPERAND.GR3, "GR3");
			put(OPERAND.GR4, "GR4");
			put(OPERAND.GR5, "GR5");
			put(OPERAND.GR6, "GR6");
			put(OPERAND.GR7, "GR7");
			put(OPERAND.GR8, "GR8");
		}
	};


	private void libAppend() {
		if((libFlag & 1) == 1) {
			tailProgram += LIBMULT;
		}
		if((((libFlag >> 1) & 1)| ((libFlag >> 2) & 1)) == 1) { //WRTINT use DIV 
			tailProgram += LIBDIV;
		}
		if(((libFlag >> 2) & 1) == 1) {
			tailProgram += LIBWRTINT;
		}
		if(((libFlag >> 3) & 1) == 1) {
			tailProgram += LIBWRTCH;
		}
		if(((libFlag >> 4) & 1) == 1) {
			tailProgram += LIBWRTSTR;
		}
		if(((libFlag >> 5) & 1) == 1) {
			tailProgram += LIBWRTLN;
		}
		if(((libFlag >> 6) & 1) == 1) {
			tailProgram += LIBEQUAL;
		}
	};

	final String LIBMULT = 
			";============================================================\n"
					+ "; MULT: 掛け算を行うサブルーチン\n"
					+ "; GR1 * GR2 -> GR2\n"
					+ "MULT	START\n"
					+ "	PUSH	0,GR3	; GR3の内容をスタックに退避\n"
					+ "	LAD	GR3,0	; GR3を初期化\n"
					+ "	LD	GR4,GR2\n"
					+ "	JPL	LOOP\n"
					+ "	XOR	GR4,=#FFFF\n"
					+ "	ADDA	GR4,=1\n"
					+ "LOOP	SRL	GR4,1\n"
					+ "	JOV	ONE\n"
					+ "	JUMP	ZERO\n"
					+ "ONE	ADDL	GR3,GR1\n"
					+ "ZERO	SLL	GR1,1\n"
					+ "	AND	GR4,GR4\n"
					+ "	JNZ	LOOP\n"
					+ "	CPA	GR2,=0\n"
					+ "	JPL	END\n"
					+ "	XOR	GR3,=#FFFF\n"
					+ "	ADDA	GR3,=1\n"
					+ "END	LD	GR2,GR3\n"
					+ "	POP	GR3\n"
					+ "	RET\n"
					+ "	END\n";
	final String LIBDIV = 
			";============================================================\n"
					+ "; DIV 割り算を行うサブルーチン\n"
					+ "; GR1 / GR2 -> 商は GR2, 余りは GR1\n"
					+ "DIV	START\n"
					+ "	PUSH	0,GR3\n"
					+ "	LD	GR0,GR1\n"
					+ "	ST	GR2,B\n"
					+ "	CPA	GR1,=0\n"
					+ "	JPL	SKIPA\n"
					+ "	XOR	GR1,=#FFFF\n"
					+ "	ADDA	GR1,=1\n"
					+ "SKIPA	CPA	GR2,=0\n"
					+ "	JZE	SKIPD\n"
					+ "	JPL	SKIPB\n"
					+ "	XOR	GR2,=#FFFF\n"
					+ "	ADDA	GR2,=1\n"
					+ "SKIPB	LD	GR3,=0\n"
					+ "LOOP	CPA	GR1,GR2\n"
					+ "	JMI	STEP\n"
					+ "	SUBA	GR1,GR2\n"
					+ "	LAD	GR3,1,GR3\n"
					+ "	JUMP	LOOP\n"
					+ "STEP	LD	GR2,GR3\n"
					+ "	LD	GR3,GR0\n"
					+ "	CPA	GR3,=0\n"
					+ "	JPL	SKIPC\n"
					+ "	XOR	GR1,=#FFFF\n"
					+ "	ADDA	GR1,=1\n"
					+ "SKIPC	XOR	GR3,B\n"
					+ "	CPA	GR3,=0\n"
					+ "	JZE	SKIPD\n"
					+ "	JPL	SKIPD\n"
					+ "	XOR	GR2,=#FFFF\n"
					+ "	ADDA	GR2,=1\n"
					+ "SKIPD	POP	GR3\n"
					+ "	RET\n"
					+ "B	DS	1\n"
					+ "	END\n";
	final String LIBWRTINT = 
			";============================================================\n"
					+ "; GR2の内容（数値データ）を出力装置に書き出すサブルーチン\n"
					+ "WRTINT	START\n"
					+ "	PUSH	0,GR2	; 数値データをもう一度スタックに退避\n"
					+ "	LD	GR3,=0	; GR3はインデックスとして用いる\n"
					+ "	; 数値データが負数である場合は，正の数に変換\n"
					+ "	CPA	GR2,=0\n"
					+ "	JPL	LOOP1\n"
					+ "	XOR	GR2,=#FFFF\n"
					+ "	ADDA	GR2,=1\n"
					+ "	; 数値データを変換しながら，バッファに格納\n"
					+ "LOOP1	LD	GR1,GR2\n"
					+ "	LD	GR2,=10\n"
					+ "	CALL	DIV\n"
					+ "	XOR	GR1,=#0030\n"
					+ "	ST	GR1,BUFFER,GR3\n"
					+ "	LAD	GR3,1,GR3\n"
					+ "	CPA	GR2,=0\n"
					+ "	JNZ	LOOP1\n"
					+ "	; 数値データが負数であれば，'-'を追加\n"
					+ "	POP	GR2\n"
					+ "	CPA	GR2,=0\n"
					+ "	JZE	LOOP2\n"
					+ "	JPL	LOOP2\n"
					+ "	LD	GR1,='-'\n"
					+ "	ST	GR1,BUFFER,GR3\n"
					+ "	LAD	GR3,1,GR3\n"
					+ "	; BUFFERを逆順にたどりながら，出力用バッファに格納\n"
					+ "LOOP2	LAD	GR3,-1,GR3\n"
					+ "	LD	GR1,BUFFER,GR3\n"
					+ "	LD	GR2,GR7\n"
					+ "	ADDA	GR2,GR6\n"
					+ "	ST	GR1,0,GR2\n"
					+ "	LAD	GR6,1,GR6\n"
					+ "	CPA	GR3,=0\n"
					+ "	JNZ	LOOP2\n"
					+ "END	RET\n"
					+ "BUFFER	DS	6\n"
					+ "	END\n";
	final String LIBWRTCH = 
			";============================================================\n"
					+ "; GR2の内容（文字）を出力装置に書き出すサブルーチン\n"
					+ "WRTCH	START\n"
					+ "	PUSH	0,GR1	; GR1の内容をスタックに退避\n"
					+ "	LD	GR1,GR7\n"
					+ "	ADDA	GR1,GR6	; GR1に次の文字を格納する番地を代入\n"
					+ "	ST	GR2,0,GR1\n"
					+ "	LAD	GR6,1,GR6\n"
					+ "	POP	GR1\n"
					+ "	RET\n"
					+ "	END\n";
	final String LIBWRTSTR = 	
			";============================================================\n"
					+ "; GR2の指すメモリ番地から，長さGR1の文字列を出力装置に書き出すサブルーチン\n"
					+ "WRTSTR	START\n"
					+ "	LAD	GR3,0	; GR3は制御変数として用いる\n"
					+ "LOOP	CPA	GR3,GR1\n"
					+ "	JZE	END\n"
					+ "	LD	GR4,GR2\n"
					+ "	ADDA	GR4,GR3	; 出力する文字の格納番地を計算\n"
					+ "	LD	GR5,0,GR4	; 出力する文字をレジスタにコピー\n"
					+ "	LD	GR4,GR7\n"
					+ "	ADDA	GR4,GR6	; 出力先の番地を計算\n"
					+ "	ST	GR5,0,GR4	; 出力装置に書き出し\n"
					+ "	LAD	GR3,1,GR3\n"
					+ "	LAD	GR6,1,GR6\n"
					+ "	JUMP	LOOP\n"
					+ "END RET\n"
					+ "	END\n";
	final String LIBWRTLN = 
			";============================================================\n"
					+ "; 改行を出力装置に書き出すサブルーチン\n"
					+ "; 実質的には，GR7で始まるアドレス番地から長さGR6の文字列を出力する\n"
					+ "WRTLN	START\n"
					+ "	ST	GR6,OUTLEN\n"
					+ "	LAD	GR1,0\n"
					+ "LOOP	CPA	GR1,OUTLEN\n"
					+ "	JZE	END\n"
					+ "	LD	GR2,GR7\n"
					+ "	ADDA	GR2,GR1\n"
					+ "	LD	GR3,0,GR2\n"
					+ "	ST	GR3,OUTSTR,GR1\n"
					+ "	LAD	GR1,1,GR1\n"
					+ "	JUMP	LOOP\n"
					+ "END	OUT	OUTSTR,OUTLEN\n"
					+ "	LAD	GR6,0	; 文字列を出力して，GR6を初期化\n"
					+ "	RET\n"
					+ "OUTSTR	DS	256\n"
					+ "OUTLEN	DS	1\n"
					+ "	END\n";
	final String LIBEQUAL = 
			";========================================================\n"
					+ "; extended library set GR2 1 (if GR1 == GR2) 0 (else)\n"
					+ "EQUAL	START\n"
					+ " CPA     GR1,  GR2\n"
					+ " JZE     ZERO\n"
					+ " LAD     GR2, 0\n"
					+ " JUMP    FIN\n"
					+ "ZERO LAD GR2, 1\n"
					+ "FIN RET\n"
					+ " END\n";

}
