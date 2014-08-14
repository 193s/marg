import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

import lang.Extension;


public class Main {
	public static void main(String args[]) {
		String s = "a=(1+2)*3;a";
		System.out.println(s + '\n');	//入力 + 改行
		Token[] ls = tokenizer(s);	// 字句解析
		for (Token t: ls) System.out.println(" [" + t + ']');	// 字句解析の結果を出力
		System.out.println();	//改行
		TokenInput input = new TokenInput(ls);
		AST ast = new Program(input);	// 構文解析
		
		Environment e = new Environment();
		ast.eval(0, e);
		System.out.println(e.hashMap.size());
		for(Entry<String, Integer> entry : e.hashMap.entrySet()) {
			System.out.println(entry.getKey() +" : " + entry.getValue());
		}
	}

	public static Token[] tokenizer(String s) {
		String[][] m = Extension.matchAll(s, "\\s*(([0-9]+)|([\\(\\)-+*/=;])|([a-zA-z]+))\\s*");
		Token[] ret = new Token[m.length];
		for (int i=0; i<m.length; i++) {
			if		(m[i][2] != null) ret[i] = new Token.Num(Integer.parseInt(m[i][2]));
			else if	(m[i][3] != null) ret[i] = new Token.Operator(m[i][3]);
			else if (m[i][4] != null) ret[i] = new Token.Name(m[i][4]);
		}
		return ret;
	}

	
	static class TokenInput implements Cloneable {
		Token[] tokens;
		int offset = 0;
		int length;
		
		TokenInput(Token[] tokens) {
			this.tokens = tokens;
			length = tokens.length;
		}
		Token getNext() {
			return tokens[offset+1];
		}
		Token get() {
			return tokens[offset];
		}
		Token get(int skip) {
			return tokens[offset + skip];
		}
		
		
		@Override
		public TokenInput clone() {
			TokenInput r;
			try {
				r = (TokenInput)super.clone();
				r.offset = offset;
				r.tokens = tokens;
				r.length = length;
			}
			catch (CloneNotSupportedException e) {
				throw new RuntimeException();
			}
			return r;
		}
		
		TokenInput clone(int skip) {
			TokenInput r;
			try {
				r = (TokenInput)super.clone();
				r.offset = skip + offset;
				r.tokens = tokens;
				r.length = length;
			}
			catch (CloneNotSupportedException e) {
				throw new RuntimeException();
			}
			return r;
		}
	}
	
	
	/*	
	 * 	Num ::= '(' Sum ')' | NumberToken | Variable
	 *	Sum ::= Prod { [+-] Prod }
	 *	Assign ::= Variable '=' Num
	 *	Statement ::= Assign | Sum
	 *	Program ::= Statement {';' Statement? }
	 */
	static class AST {
		// その要素に含まれる合計のトークン数
		int num_token = 0;
		// 構文木の構築に成功したかどうか
		boolean ok = false;
		int eval(int k, Environment e) {
			return 0;
		}
	}
	
	
	static class ASTList extends AST {
		ArrayList<AST> children;
		ASTList() {
			children = new ArrayList<AST>();
		}
	}
	
	
	static class ASTLeaf extends AST {
		Token child;
		ASTLeaf() {
		}
		ASTLeaf(Token l) {
			child = l;
		}
	}

	
	static class Num extends ASTList {
		Num(TokenInput input) {
			Token nextToken = input.get();
			if (nextToken instanceof Token.Operator) {
				if(!((Token.Operator)nextToken).getValue().equals("(")) return;
				AST s = new Sum(input.clone(1));
				if(!s.ok) return;
				if(input.offset + s.num_token + 1 >= input.length) return;
				if(!(input.get(s.num_token + 1) instanceof Token.Operator)) return;
				if(!((Token.Operator)input.get(s.num_token + 1)).getValue().equals(")")) return;
				num_token = 1 + s.num_token + 1;
				children.add(s);
			}

			else if (nextToken instanceof Token.Num) {
				children.add(new ASTLeaf(nextToken));
				num_token = 1;
			}
			
			else if (nextToken instanceof Token.Name) {
				String ident = (String)input.get().getValue();
				Variable v = new Variable(ident);
				children.add(v);
				num_token = 1;
			}
			
			ok = true;
		}
		
		@Override
		int eval(int k, Environment e) {
			for(int i=0; i<k; i++) System.out.print(" ");
			System.out.println("Num");
			AST child = children.get(0);
			if		(child instanceof Sum)		return child.eval(k + 1, e);
			else if (child instanceof Variable) return child.eval(k + 1, e);
			else /* child instanceof ASTLeaf */ return ((Token.Num)((ASTLeaf)child).child).getValue();
		}
	}
	
	
	static class Sum extends ASTList {
		Sum(TokenInput input) {
			AST left = new Prod(input.clone());
			if (!left.ok) return;
			children.add(left);
			num_token = left.num_token;
			
			while (input.offset + num_token + 1 < input.length) {
				Token nextToken = input.get(num_token);
				
				if (!(nextToken instanceof Token.Operator)) break;
				
				if (!(((Token.Operator) nextToken).getValue().equals("+")) &&
					!(((Token.Operator) nextToken).getValue().equals("-"))) break;
				
				AST right = new Prod(input.clone(num_token + 1));
				if(!right.ok) break;
				children.add(new ASTLeaf(nextToken));
				children.add(right);
				
				num_token += right.num_token + 1;
			}
			ok = true;
		}
		
		@Override
		int eval(int k, Environment e) {
			for(int i=0; i<k; i++) System.out.print(' ');
			System.out.println("sum");
			int ret = children.get(0).eval(k + 1, e);
			for(int i=1; i<children.size(); i+=2) {
				for(int j=0; j<k; j++) System.out.print(' ');
				System.out.println( ((ASTLeaf)children.get(i)).child.toString() );
				
				int right = children.get(i + 1).eval(k + 1, e);
				Token.Operator operator = (Token.Operator) ((ASTLeaf)children.get(i)).child;
				if		(operator.getValue().equals("+")) ret += right;
				else if (operator.getValue().equals("-")) ret -= right;
			}
			return ret;
		}
	}
	
	
	static class Prod extends ASTList {
		Prod(TokenInput input) {
			AST left = new Num(input.clone());
			if (!left.ok) return;
			children.add(left);
			num_token = left.num_token;
			
			while (input.offset + num_token + 1 < input.length) {

				Token nextToken = input.get(num_token);
				if (!(nextToken instanceof Token.Operator)) break;

				if (!(((Token.Operator)(nextToken)).getValue().equals("*")) &&
					!(((Token.Operator)(nextToken)).getValue().equals("/"))) break;
				
				AST right = new Num(input.clone(num_token + 1));
				if (!right.ok) break;
				
				children.add(new ASTLeaf(nextToken));
				children.add(right);
				
				num_token += right.num_token + 1;
			}
			ok = true;
		}
		
		@Override
		int eval(int k, Environment e) {
			for (int i=0; i<k; i++) System.out.print(' ');
			System.out.println("prod");
			int ret = children.get(0).eval(k + 1, e);
			
			for (int i=1; i<children.size(); i+=2) {
				for (int j=0; j<k; j++) System.out.print(' ');

				System.out.println( ((ASTLeaf)children.get(i)).child.toString() );

				int right = children.get(i + 1).eval(k + 1, e);

				Token.Operator operator = (Token.Operator) (((ASTLeaf)children.get(i)).child);
				if		(operator.getValue().equals("*")) ret *= right;
				else if (operator.getValue().equals("/")) ret /= right;
			}
			return ret;
		}
	}

	
	static class Statement extends ASTList {
		Statement(TokenInput input) {
			AST child = new Assign(input.clone());
			if (!child.ok) {
				child = new Sum(input.clone());
				if (!child.ok) return;
			}
			children.add(child);
			num_token = child.num_token;
			ok = true;
		}

		@Override
		int eval(int k, Environment e) {
			for (int i=0; i<k; i++) System.out.print(' ');

			AST child = children.get(0);
//			if		(child instanceof Assign) child.eval(k+1, e);
//			else if (child instanceof Sum)	  child.eval(k+1, e);
			return child.eval(k+1, e);
		}
	}
	
	
	static class Program extends ASTList {
		Program(TokenInput input) {
			AST s = new Statement(input);
			if (!s.ok) return;
			children.add(s);
			num_token = s.num_token;

			while (input.offset + num_token + 1 < input.length) {
				Token nextToken = input.getNext();
				if (!(nextToken instanceof Token.Operator)) break;
				
				String op = ((Token.Operator)nextToken).getValue();
				if (!op.equals(";")) break;
				children.add(new ASTLeaf(nextToken));
				num_token++;
				
				AST right = new Statement(input.clone(2));
				if (!right.ok) continue;
				children.add(right);
				num_token += right.num_token;
			}
			ok = true;
		}
		
		@Override
		int eval(int k, Environment e) {
			for (int i=0; i<k; i++) System.out.println(' ');
			int ret = children.get(0).eval(k+1, e);
			for (int i=1; i<children.size(); i+=2) {
				for (int j=0; j<k; j++) System.out.print(' ');
				System.out.println(((ASTLeaf)(children.get(i))).child.toString());
				int right = children.get(i+1).eval(k+1, e);
				ret += right;	//FIXME
			}
			return ret;
		}
	}

	
	static class Assign extends ASTList {
		Assign(TokenInput input) {
			if (input.offset + 2 >= input.length) return;
			
			Token nextToken = input.get();
			if (!(nextToken instanceof Token.Name)) return;
			AST left = new Variable((String)nextToken.getValue());
			
			if (!(input.getNext() instanceof Token.Operator)) return;
			if (!(input.getNext().getValue().equals("="))) return;
			
			AST right = new Statement(input.clone(2));
			if (!right.ok) return;
			
			children.add(left);
			children.add(new ASTLeaf(input.getNext()));
			children.add(right);

			num_token += 3;
			
			ok = true;
		}
		@Override
		int eval(int k, Environment e) {
			for(int i=0; i<k; i++) System.out.print(' ');
			System.out.println("Assign");
			int ret = ((Statement)children.get(2)).eval(k+1, e);
			e.hashMap.put(((Variable)children.get(0)).name, ret);
			return ret;
		}
	}
	
	
	static class Variable extends ASTLeaf {
		String name;
		Variable(String name) {
			this.name = name;
			num_token = 1;
		}
		@Override
		int eval(int k, Environment e) {
			Integer v = e.hashMap.get(name);
			return v == null? 0: v;
		}
	}
	
	
	static class Environment { 
		HashMap<String, Integer> hashMap;
		Environment() {
			hashMap = new HashMap<String, Integer>();
		}
	}
}
