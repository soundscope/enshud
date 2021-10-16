package enshud.s2.parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;


public class Parser {

	/**
	 * サンプルmainメソッド．
	 * 単体テストの対象ではないので自由に改変しても良い．
	 */
	public static void main(final String[] args) {
		// normalの確認
		//new Parser().run("data/ts/normal01.ts");
		//new Parser().run("data/ts/normal17.ts");

		// synerrの確認
		//new Parser().run("data/ts/synerr03.ts");
		//new Parser().run("data/ts/synerr02.ts");
	}

	/**
	 * TODO
	 * 
	 * 開発対象となるParser実行メソッド．
	 * 以下の仕様を満たすこと．
	 * 
	 * 仕様:
	 * 第一引数で指定されたtsファイルを読み込み，構文解析を行う．
	 * 構文が正しい場合は標準出力に"OK"を，正しくない場合は"Syntax error: line"という文字列とともに，
	 * 最初のエラーを見つけた行の番号を標準エラーに出力すること （例: "Syntax error: line 1"）．
	 * 入力ファイル内に複数のエラーが含まれる場合は，最初に見つけたエラーのみを出力すること．
	 * 入力ファイルが見つからない場合は標準エラーに"File not found"と出力して終了すること．
	 * 
	 * @param inputFileName 入力tsファイル名
	 */
	List<String> buffer;
	List<String []> splittedBuffer;
	int index;
	String[] st;
	int maxDepth;
	public void run(final String inputFileName) {
		index = 0;
		buffer = null;
		maxDepth = 0;
		splittedBuffer = new ArrayList<String []>();
		try {
			buffer = Files.readAllLines(Paths.get(inputFileName));
		} catch (IOException e) {
			System.err.println("File not found");
			return;
		}
		for(String x: buffer) {
			splittedBuffer.add(x.split("\t"));
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
		else System.err.println("Syntax error: line " + splittedBuffer.get(maxDepth)[3]);
	}


	protected boolean program() {
		st = getToken();
		// program

		if(! st[2].equals("17")) {
			return false;
		}

		if(! programName()) {
			return false;
		}
		st = getToken();
		// ;

		if(! st[2].equals("37")) {
			return false;
		}

		if(! block()) {
			return false;
		}
		
		if(! compoundSentence()) {
			return false;
		}

		st = getToken();
		// .
		if(! st[2].equals("42")) {
			return false;
		}
		return true;
	}

	protected boolean programName() {
		st = getToken();
		// name
		if(! st[2].equals("43")) {
			return false;
		}
		return true;
	}
	protected boolean block() {
		if(! variableDeclaration()) {
			return false;
		}

		if(! subProgramDeclarations()) {
			return false;
		}

		return true;
	}
	protected boolean variableDeclaration() {
		int save_index = index;
		st = getToken();

		//var
		if(st[2].equals("21")) {
			if(! variableDeclarationList()) {
				return false;
			}
		} else {
			index = save_index;
		}

		return true;
	}
	protected boolean variableDeclarationList() {
		if(! variableNameList()) {
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
			if(variableNameList()) {
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
	protected boolean variableNameList() {
		if(! variableName()) {
			return false;
		}
		for(;;) {
			int save_index = index;
			st = getToken();
			if(st[2].equals("41")) {
				if(! variableName()) {
					return false;
				}
			} else {
				index = save_index;
				break;
			}
		}
		return true;
	}
	protected boolean variableName() {
		st = getToken();
		// name
		if(! st[2].equals("43")) {
			return false;
		}
		return true;
	}

	protected boolean type() {
		int save_index = index;

		if(! standardType()) {
			index = save_index;
			if(! arrayType()) {
				return false;
			} else {
				return true;
			}
		}
		return true;
	}	

	protected boolean standardType() {
		st = getToken();

		//boolean, char, integer
		if(st[2].equals("3") || st[2].equals("4") || st[2].equals("11")) {
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
		if(! standardType()) {
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
		int save_index = index;
		st = getToken();
		// + or -
		if(st[2].equals("30") || st[2].equals("31")) {
			;
		} else {
			index = save_index;
		}

		st = getToken();
		// unsigned int
		if (! st[2].equals("44")) {
			return false;
		}
		return true;
	}

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

		if(! variableDeclaration()) {
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

		if(! procedureName()) {
			return false;
		} 

		if(! formalParameter()) {
			return false;
		}

		st = getToken();
		// ;
		if(! st[2].equals("37")) {
			return false;
		}
		return true;
	}

	protected boolean procedureName() {
		st = getToken();
		// name
		if(! st[2].equals("43")) {
			return false;
		}
		return true;
	}

	protected boolean formalParameter() {
		int save_index = index;
		st = getToken();
		// (
		if(st[2].equals("33")) {

			if(! formalParameterList()) {
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

	protected boolean formalParameterList() {
		if(! formalParameterNameList()) {
			return false;
		}
		st = getToken();

		// :
		if(! st[2].equals("38")) {
			return false;
		}
		if(! standardType()) {
			return false;
		}

		for(;;) {
			int save_index = index;
			st = getToken();
			// ;
			if(st[2].equals("37")) {
				if(! formalParameterNameList()) {
					return false;
				}
				st = getToken();
				// :
				if(! st[2].equals("38")) {
					return false;
				}
				if(! standardType()) {
					return false;
				}
			} else {
				index = save_index;
				break;
			}
		}
		return true;
	}

	protected boolean formalParameterNameList() {
		if(! formalParameterName()) {
			return false;
		}

		for(;;) {
			int saved_index = index;
			st =  getToken();
			//,
			if(st[2].equals("41")) {
				if(! formalParameterName()) {
					return false;
				}	
			} else {
				index = saved_index;
				break;
			}
		}
		return true;
	}
	protected boolean formalParameterName() {
		st = getToken();
		// name
		if(! st[2].equals("43")) {
			return false;
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
				if (! expression()) {
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

				if(! expression()) {
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
				if(! expressionList()) {
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
		} else {
			return false;
		}
		//dead code
		return true;
	}


	protected boolean variableList() {
		if(! variable()) {
			return false;
		}
		for(;;) {
			int save_index = index;
			if(st[2].equals("41")) {
				if(! variable()) {
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
		if(! leftExpression()) {
			return false;
		}

		st = getToken();
		if(! st[2].equals("40")) {
			return false;
		}

		if(! expression()) {
			return false;
		}

		return true;
	}

	protected boolean leftExpression() {
		if(! variable()) {
			return false;
		}
		return true;
	}
	
	//should use left factoring
	protected boolean variable() {
		int save_index = index;
		if(! indexedVariable()) {
			index = save_index;
			if(! pureVariable()) {
				return false;
			} else {
				return true;
			}
		} 

		return true;
	}

	protected boolean pureVariable() {
		if(! variableName()) {
			return false;
		}
		return true;
	}

	protected boolean indexedVariable() {
		if(! variableName()) {
			return false;
		}
		st = getToken();
		//[
		if(! st[2].equals("35")) {
			return false;
		}

		if(! index()) {
			return false;
		}

		st = getToken();
		//]
		if(! st[2].equals("36")) {
			return false;
		}

		return true;
	}

	protected boolean index() {
		if(! expression()) {
			return false;
		}
		return true;
	}

	protected boolean procedureCallSentence() {
		if(! procedureName()) {
			return false;
		}

		int save_index = index;
		st = getToken();
		//(
		if(st[2].equals("33")) {

			if(! expressionList()) {
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

	protected boolean expressionList() {
		if(! expression()) {
			return false;
		}

		for(;;) {
			int save_index = index;
			st = getToken();
			// ,
			if(st[2].equals("41")) {
				if(! expression()) {
					return false;
				}
			} else {
				index = save_index;
				break;
			}
		}
		return true;
	}

	protected boolean expression() {

		if(! simpleExpression()) {
			return false;
		}

		int save_index = index;
		
		if(relationOperator()) {

			if(! simpleExpression()) {
				return false;
			}
		} else {
			index = save_index;
		}
		return true;
	}

	protected boolean simpleExpression() {
		int save_index = index;
		st = getToken();

		// + or -
		if(st[2].equals("30") || st[2].equals("31")) {
			;
		} else {
			index = save_index;
		}

		if(! term()) {
			return false;
		}

		for(;;) {
			int save_index2 = index;
			if(additiveperator()) {

				if(! term()) {
					return false;
				}
			} else {
				index = save_index2;
				break;
			}
		}
		return true;
	}

	protected boolean term() {

		if(! divisor()) {
			return false;
		}

		for(;;) {
			int save_index = index;
			if (multiplicativeOperator()) {
				if (! divisor()) {

					return false;
				}
			} else {
				index = save_index;
				break;
			}
		}

		return true;
	}

	protected boolean divisor() {
		int save_index = index;
		if(! variable()) {
			index = save_index;
			if(! constant()) {
				index = save_index;

				st = getToken();
				// (
				if(st[2].equals("33")) {
					if(! expression()) {
						return false;
					}
					st = getToken();
					// )
					if(! st[2].equals("34")) {
						return false;
					}
					//not
				} else if(st[2].equals("13")) {
					if(! divisor()) {
						return false;
					}
				} else {
					return false;
				}
			}
		}


		return true;
	}


	private boolean constant() {
		st = getToken();
		// unsigned int
		if (st[2].equals("44")) {
			return true;
			//string
		} else if(st[2].equals("45")) {
			return true;
			// false
		} else if(st[2].equals("9")) {
			return true;
			// true
		} else if(st[2].equals("20")) {
			return true;
		}
		return false;
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

}
