package enshud.s1.lexer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Lexer {

	/**
	 * サンプルmainメソッド．
	 * 単体テストの対象ではないので自由に改変しても良い．
	 * @throws IOException 
	 */
	public static void main(final String[] args) throws IOException {
		// normalの確認
		new Lexer().run("data/pas/normal04.pas", "tmp/out4.ts");
		//new Lexer().run("data/pas/normal02.pas", "tmp/out2.ts");
		//new Lexer().run("data/pas/normal03.pas", "tmp/out3.ts");
	}

	/**
	 * TODO
	 * 
	 * 開発対象となるLexer実行メソッド．
	 * 以下の仕様を満たすこと．
	 * 
	 * 仕様:
	 * 第一引数で指定されたpasファイルを読み込み，トークン列に分割する．
	 * トークン列は第二引数で指定されたtsファイルに書き出すこと．
	 * 正常に処理が終了した場合は標準出力に"OK"を，
	 * 入力ファイルが見つからない場合は標準エラーに"File not found"と出力して終了すること．
	 * 
	 * @param inputFileName 入力pasファイル名
	 * @param outputFileName 出力tsファイル名
	 */
	protected enum State {
		DELIMITER,
		CAPTION,
		UNSIGNED_NUMBER,
		RESERVED_OR_VARIABLE_NAME, 
		VARIABLE_NAME,
		LESS,
		GREAT_OR_COLON,
		PERIOD,
		STRING,
		ERROR
	}
	protected StringBuffer token = new StringBuffer();

	public void run(final String inputFileName, final String outputFileName)  {
		List<String> buffer = null;
		token.setLength(0);
		try {
			buffer = Files.readAllLines(Paths.get(inputFileName));
		} catch (IOException e) {
			System.err.println("File not found");
			return;
		}
		// TODO
		State state = State.DELIMITER;
		try{
			Files.deleteIfExists(Paths.get(outputFileName));
			Files.writeString(Paths.get(outputFileName), "", StandardOpenOption.CREATE);
		}catch(IOException e){
			System.out.println(e);
		}
		int ln = 0;
		for(String line : buffer) {
			ln++;
			for(int i = 0; i < line.length(); i++) {
				char c = line.charAt(i);
				try{
					state = transit(c , state,  outputFileName, ln);
				} catch(IOException e){
					System.out.println(e);
				}
			}
			try{
				state = transit('\n' , state,  outputFileName, ln);
				System.err.println(token);
			}catch(IOException e){
				System.out.println(e);
			}
		}
		System.out.println("OK");
	}

	protected enum Ope {
		OUT_NOT_STORE,
		OUT_STORE,
		DEL_NOT_STORE,
		STORE_OUT,
		STORE
	};

	public State transit (char c, State s, String outputFileName, int ln) throws IOException {
		int id;
		switch (s) {
		//OPERATION x, int id, int ln,char c, String outputFileName)
		case DELIMITER:
			id = tokenNumberTable.get(token.toString());
			if(c == '{') {
				outPut(Ope.OUT_NOT_STORE, id, ln, c, outputFileName);
				return State.CAPTION;
			}
			if(c == ' ' || c == '\t' || c == '\n')  {
				outPut(Ope.OUT_NOT_STORE, id, ln, c, outputFileName);
				return State.DELIMITER;
			}				
			if('0' <= c && c <= '9')  {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.UNSIGNED_NUMBER;
			}	
			if(('A' <= c && c <= 'Z') || ('a' <= c && c <= 'z')) {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.RESERVED_OR_VARIABLE_NAME;
			}
			if(c == '.') {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.PERIOD;
			}
			if(c == '<') {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.LESS;
			}
			if(c == '>' || c == ':') {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.GREAT_OR_COLON;
			}
			if(isSpecialToken(c)) {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.DELIMITER;
			}
			if(c == '\'') {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.STRING;
			}
			return State.ERROR;
		case CAPTION:
			if(c != '}' || c == '\n') return State.CAPTION;
			outPut(Ope.DEL_NOT_STORE, -1, ln, c, outputFileName);
			return State.DELIMITER;
		case UNSIGNED_NUMBER:
			if('0' <= c && c <= '9') {
				outPut(Ope.STORE, -1, ln, c, outputFileName); return State.UNSIGNED_NUMBER;
			}
			id = 44;
			if(c == ' ' || c == '\t' || c == '\n') {
				outPut(Ope.OUT_NOT_STORE, id, ln, c, outputFileName);
				return State.DELIMITER;
			}	
			if(c == '{') {
				outPut(Ope.OUT_NOT_STORE, id, ln, c, outputFileName);
				return State.CAPTION;
			}	
			if(c == '\'') {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.STRING;
			}	
			if(c == ':' || c == '>') {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.GREAT_OR_COLON;
			}	
			if(c == '<') {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.LESS;
			}	
			if(c == '.') {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.PERIOD;
			}
			if(isSpecialToken(c)) {
				outPut(Ope.OUT_STORE, id, ln, c , outputFileName);
				return State.DELIMITER;
			}
			return State.ERROR;
		case RESERVED_OR_VARIABLE_NAME:
			if('0' <= c && c <= '9') {
				outPut(Ope.STORE, -1, ln, c, outputFileName);
				return State.VARIABLE_NAME;
			}
			if(('A' <= c && c <= 'Z') || ('a' <= c && c <= 'z')) {
				outPut(Ope.STORE, -1, ln, c, outputFileName);
				return State.RESERVED_OR_VARIABLE_NAME;
			}
			if(tokenNumberTable.get(token.toString()) == null) id = 43;
			else id = tokenNumberTable.get(token.toString());
			if(c == ' ' || c == '\t' || c == '\n') {
				outPut(Ope.OUT_NOT_STORE, id, ln, c, outputFileName);
				return State.DELIMITER;
			}	
			if(c == '{') {
				outPut(Ope.OUT_NOT_STORE, id, ln, c, outputFileName);
				return State.CAPTION;
			}	
			if(c == '\'') {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.STRING;
			}	
			if(c == ':' || c == '>') {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.GREAT_OR_COLON;
			}	
			if(c == '<') {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.LESS;
			}	
			if(c == '.') {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.PERIOD;
			}
			if(isSpecialToken(c)) {
				outPut(Ope.OUT_STORE, id, ln, c , outputFileName);
				return State.DELIMITER;
			}
			return State.ERROR;
		case VARIABLE_NAME:
			if('0' <= c && c <= '9') {
				outPut(Ope.STORE, -1, ln, c, outputFileName);
				return State.VARIABLE_NAME;
			}
			if(('A' <= c && c <= 'Z') || ('a' <= c && c <= 'z')) {
				return State.ERROR;
			}
			// VARIABLE_NAME 43
			id = 43;
			if(c == ' ' || c == '\t' || c == '\n') {
				outPut(Ope.OUT_NOT_STORE, id, ln, c, outputFileName);
				return State.DELIMITER;
			}	
			if(c == '{') {
				outPut(Ope.OUT_NOT_STORE, id, ln, c, outputFileName);
				return State.CAPTION;
			}	
			if(c == '\'') {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.STRING;
			}	
			if(c == ':' || c == '>') {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.GREAT_OR_COLON;
			}	
			if(c == '<') {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.LESS;
			}	
			if(c == '.') {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.PERIOD;
			}
			if(isSpecialToken(c)) {
				outPut(Ope.OUT_STORE, id, ln, c , outputFileName);
				return State.DELIMITER;
			}
			return State.ERROR;
		case PERIOD:
			//PERIOD 42
			id = 42;
			if('0' <= c && c <= '9') {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.UNSIGNED_NUMBER;
			}
			if(('A' <= c && c <= 'Z') || ('a' <= c && c <= 'z')) {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.RESERVED_OR_VARIABLE_NAME;
			}
			if(c == ' ' || c == '\t' || c == '\n') {
				outPut(Ope.OUT_NOT_STORE, id, ln, c, outputFileName);
				return State.DELIMITER;
			}	
			if(c == '{') {
				outPut(Ope.OUT_NOT_STORE, id, ln, c, outputFileName);
				return State.CAPTION;
			}	
			if(c == '\'') {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.STRING;
			}	
			if(c == ':' || c == '>') {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.GREAT_OR_COLON;
			}	
			if(c == '<') {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.LESS;
			}	
			if(c == '.') {
				outPut(Ope.STORE, -1, ln, c, outputFileName);
				return State.DELIMITER;
			}
			if(isSpecialToken(c)) {
				outPut(Ope.OUT_STORE, id, ln, c , outputFileName);
				return State.DELIMITER;
			}
			return State.ERROR;
		case LESS:
			// < 26
			id = 26;
			if('0' <= c && c <= '9') {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.UNSIGNED_NUMBER;
			}
			if(('A' <= c && c <= 'Z') || ('a' <= c && c <= 'z')) {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.RESERVED_OR_VARIABLE_NAME;
			}
			if(c == ' ' || c == '\t' || c == '\n') {
				outPut(Ope.OUT_NOT_STORE, id, ln, c, outputFileName);
				return State.DELIMITER;
			}	
			if(c == '{') {
				outPut(Ope.OUT_NOT_STORE, id, ln, c, outputFileName);
				return State.CAPTION;
			}	
			if(c == '\'') {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.STRING;
			}	
			if (c == '>') {
				// <>
				outPut(Ope.STORE, 25, ln, c, outputFileName);
				return State.DELIMITER;
			}
			if (c == '=') {
				// <=
				outPut(Ope.STORE, 27, ln, c, outputFileName);
				return State.DELIMITER;
			}
			if(c == ':' ) {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.GREAT_OR_COLON;
			}	
			if(c == '<') {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.LESS;
			}	
			if(c == '.') {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.PERIOD;
			}
			if(isSpecialToken(c)) {
				outPut(Ope.OUT_STORE, id, ln, c , outputFileName);
				return State.DELIMITER;
			}
			return State.ERROR;
		case GREAT_OR_COLON:
			id = tokenNumberTable.get(token.toString());
			if('0' <= c && c <= '9') {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.UNSIGNED_NUMBER;
			}
			if(('A' <= c && c <= 'Z') || ('a' <= c && c <= 'z')) {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.RESERVED_OR_VARIABLE_NAME;
			}
			if(c == ' ' || c == '\t' || c == '\n') {
				outPut(Ope.OUT_NOT_STORE, id, ln, c, outputFileName);
				return State.DELIMITER;
			}	
			if(c == '{') {
				outPut(Ope.OUT_NOT_STORE, id, ln, c, outputFileName);
				return State.CAPTION;
			}	
			if(c == '\'') {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.STRING;
			}	
			if (c == '=') {
				//38 : , 40 := , 28 >
				id = (id == 38 ? 40 : 28);
				outPut(Ope.STORE, id, ln, c, outputFileName);
				return State.DELIMITER;
			}
			if(c == ':' ) {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.GREAT_OR_COLON;
			}	
			if(c == '<') {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.LESS;
			}	
			if(c == '.') {
				outPut(Ope.OUT_STORE, id, ln, c, outputFileName);
				return State.PERIOD;
			}
			if(isSpecialToken(c)) {
				outPut(Ope.OUT_STORE, id, ln, c , outputFileName);
				return State.DELIMITER;
			}
			return State.ERROR;
		case STRING:
			// STRING 45
			if(c == '\'') {
				outPut(Ope.STORE_OUT, 45, ln, c, outputFileName);
				return State.DELIMITER;
			}	
			if(c == '\n') return State.ERROR;
			outPut(Ope.STORE, -1, ln, c, outputFileName);
			return State.STRING;
		default:
			break;
		}
		//dead code
		return State.ERROR;
	}


	public void outPut(Ope x, int id, int ln,char c, String outputFileName) throws IOException {
		String tmp;
		switch(x) {
		case OUT_NOT_STORE:
			if(id >= 0) {
				tmp = token + "\t" + tokenNameTable[id] + "\t" + id + "\t" + ln + "\n";
				Files.writeString(Paths.get(outputFileName), tmp, StandardOpenOption.APPEND);
			}
			token.setLength(0);
			return;
		case OUT_STORE:
			if(id >= 0) {
				tmp = token + "\t" + tokenNameTable[id] + "\t" + id + "\t" + ln + "\n";
				Files.writeString(Paths.get(outputFileName), tmp, StandardOpenOption.APPEND);
			}
			token.setLength(0);
			token.append(c);
			return;
		case DEL_NOT_STORE:
			token.setLength(0);
			return;
		case STORE_OUT: // only: STRING -> DELIMITER
			token.append(c);
			// STRING
			id = 45;
			tmp = token + "\t" + tokenNameTable[id] + "\t" + id + "\t" + ln + "\n";
			Files.writeString(Paths.get(outputFileName), tmp , StandardOpenOption.APPEND);
			token.setLength(0);;
			return;
		case STORE:
			token.append(c);
			return;
		}
	}

	private boolean isSpecialToken(char c) {
		return (c == '+' || c == '-' || c == '*' || c == '/' || c == '=' || 
				c == '(' || c == ')' || c == '['  || c == ']'|| c == ',' || c == ';');
	}


	protected Map<String, Integer> tokenNumberTable =new HashMap<String, Integer>() {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		{
			put("",-1);
			put("and", 0);
			put("array", 1);
			put("begin", 2);
			put("boolean", 3);
			put("char", 4);
			put("div", 5);
			put("/", 5);
			put("do", 6);
			put("else", 7);
			put("end", 8);
			put("false", 9);
			put("if", 10);
			put("integer", 11);
			put("mod", 12);
			put("not", 13);
			put("of", 14);
			put("or", 15);
			put("procedure", 16);
			put("program", 17);
			put("readln", 18);
			put("then", 19);			
			put("true", 20);
			put("var", 21);
			put("while", 22);
			put("writeln", 23);
			put("=", 24);
			put("<>", 25);
			put("<", 26);
			put("<=", 27);
			put(">=", 28);
			put(">", 29);
			put("+", 30);
			put("-", 31);
			put("*", 32);
			put("(", 33);
			put(")", 34);
			put("[", 35);
			put("]", 36);
			put(";", 37);
			put(":", 38);
			put("..", 39);
			put(":=", 40);
			put(",", 41);
			put(".", 42);
		}
	};

	protected String tokenNameTable[] = {
			"SAND",
			"SARRAY",
			"SBEGIN",
			"SBOOLEAN",
			"SCHAR",
			"SDIVD",
			"SDO",
			"SELSE",
			"SEND",
			"SFALSE",
			"SIF",
			"SINTEGER",
			"SMOD",
			"SNOT",
			"SOF",
			"SOR",
			"SPROCEDURE",
			"SPROGRAM",
			"SREADLN",
			"STHEN",
			"STRUE",
			"SVAR",
			"SWHILE",
			"SWRITELN",
			"SEQUAL",
			"SNOTEQUAL",
			"SLESS",
			"SLESSEQUAL",
			"SGREATEQUAL",
			"SGREAT",
			"SPLUS",
			"SMINUS",
			"SSTAR",
			"SLPAREN",
			"SRPAREN",
			"SLBRACKET",
			"SRBRACKET",
			"SSEMICOLON",
			"SCOLON",
			"SRANGE",
			"SASSIGN",
			"SCOMMA",
			"SDOT",
			"SIDENTIFIER",
			"SCONSTANT",
			"SSTRING",
	};
}
