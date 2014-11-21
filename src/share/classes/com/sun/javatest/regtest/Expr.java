/*
 * Copyright (c) 2002, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.sun.javatest.regtest;

import static com.sun.javatest.regtest.Expr.Token.*;

/**
 * Class to support simple expressions for @requires.
 */
public abstract class Expr {

    public static class Fault extends Exception {
        Fault(String msg) {
            super(msg);
        }
    };

    public interface Context {
        boolean isValidName(String name);
        String get(String name);
    }

    public static Expr parse(String s, Context c) throws Fault {
        Parser p = new Parser(s, c);
        return p.parse();
    }

    public abstract String eval(Context c) throws Fault;

    public boolean evalBoolean(Context c) throws Fault {
        String s = eval(c);
        if (s.equals("true")) {
            return true;
        } else if (s.equals("false")) {
            return false;
        } else {
            throw new Fault("invalid boolean value: `" + s + "' for expression `" + this + "'");
        }
    }

    public long evalNumber(Context c) throws Fault {
        String s = eval(c);
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException ex) {
            throw new Fault("invalid numeric value: " + s);
        }
    }

    abstract int precedence();

    Expr order() {
        return this;
    }

    static class Parser {

        Parser(String text, Context context) throws Fault {
            this.context = context;
            this.text = text;
            nextToken();
        }

        Expr parse() throws Fault {
            Expr e = parseExpr();
            expect(END);
            return e;
        }

        Expr parseExpr() throws Fault {
            for (Expr e = parseTerm(); e != null; e = e.order()) {
                switch (token) {
                    case ADD:
                        nextToken();
                        e = new AddExpr(e, parseTerm());
                        break;
                    case AND:
                        nextToken();
                        e = new AndExpr(e, parseTerm());
                        break;
                    case DIV:
                        nextToken();
                        e = new DivideExpr(e, parseTerm());
                        break;
                    case EQ:
                        nextToken();
                        e = new EqualExpr(e, parseTerm());
                        break;
                    case GE:
                        nextToken();
                        e = new GreaterEqualExpr(e, parseTerm());
                        break;
                    case GT:
                        nextToken();
                        e = new GreaterExpr(e, parseTerm());
                        break;
                    case LE:
                        nextToken();
                        e = new LessEqualExpr(e, parseTerm());
                        break;
                    case LT:
                        nextToken();
                        e = new LessExpr(e, parseTerm());
                        break;
                    case MATCH:
                        nextToken();
                        e = new MatchExpr(e, parseTerm());
                        break;
                    case MUL:
                        nextToken();
                        e = new MultiplyExpr(e, parseTerm());
                        break;
                    case NE:
                        nextToken();
                        e = new NotEqualExpr(e, parseTerm());
                        break;
                    case OR:
                        nextToken();
                        e = new OrExpr(e, parseTerm());
                        break;
                    case REM:
                        nextToken();
                        e = new RemainderExpr(e, parseTerm());
                        break;
                    case SUB:
                        nextToken();
                        e = new SubtractExpr(e, parseTerm());
                        break;
                    default:
                        return e;
                }
            }
            // bogus return to keep compiler happy
            return null;
        }

        Expr parseTerm() throws Fault {
            switch (token) {
                case NAME:
                    String id = idValue;
                    nextToken();
                    if (context.isValidName(id))
                        return new NameExpr(id);
                    else
                        throw new Fault("invalid name: " + id);

                case NOT:
                    nextToken();
                    return new NotExpr(parseTerm());

                case NUMBER:
                case TRUE:
                case FALSE:
                    String num = idValue;
                    nextToken();
                    return new NumberExpr(num);

                case LPAREN:
                    nextToken();
                    Expr e = parseExpr();
                    expect(RPAREN);
                    return new ParenExpr(e);

                case STRING:
                    String s = idValue;
                    nextToken();
                    return new StringExpr(s);

                default:
                    throw new Fault(token.getText() + " not expected");
            }
        }

        private void expect(Token t) throws Fault {
            if (t == token) {
                nextToken();
            } else {
                throw new Fault(t.getText() + "expected, but " + token.getText() + " found");
            }
        }

        private void nextToken() throws Fault {
            while (index < text.length()) {
                char c = text.charAt(index++);
                switch (c) {
                    case ' ':
                    case '\t':
                        continue;

                    case '&':
                        token = AND;
                        return;

                    case '|':
                        token = OR;
                        return;

                    case '+':
                        token = ADD;
                        return;

                    case '-':
                        token = SUB;
                        return;

                    case '*':
                        token = MUL;
                        return;

                    case '/':
                        token = DIV;
                        return;

                    case '%':
                        token = REM;
                        return;

                    case '(':
                        token = LPAREN;
                        return;

                    case ')':
                        token = RPAREN;
                        return;

                    case '<':
                        if (index < text.length() && text.charAt(index) == '=') {
                            token = LE;
                            index++;
                        } else {
                            token = LT;
                        }
                        return;

                    case '>':
                        if (index < text.length() && text.charAt(index) == '=') {
                            token = GE;
                            index++;
                        } else {
                            token = GT;
                        }
                        return;

                    case '~':
                        if (index < text.length() && text.charAt(index) == '=') {
                            token = MATCH;
                            index++;
                        } else {
                            throw new Fault("unexpected character after `~'");
                        }
                        return;

                    case '=':
                        if (index < text.length() && text.charAt(index) == '=') {
                            token = EQ;
                            index++;
                        } else {
                            throw new Fault("unexpected character after `='");
                        }
                        return;

                    case '!':
                        if (index < text.length() && text.charAt(index) == '=') {
                            token = NE;
                            index++;
                        } else {
                            token = NOT;
                        }
                        return;

                    case '\"':
                        StringBuffer sb = new StringBuffer();
                        if (index < text.length()) {
                            c = text.charAt(index);
                            index++;
                        } else {
                            throw new Fault("invalid string constant");
                        }
                        while (c != '\"') {
                            if (c == '\\') {
                                if (index < text.length()) {
                                    c = text.charAt(index);
                                    index++;
                                } else {
                                    throw new Fault("invalid string constant");
                                }
                                switch (c) {
                                    // standard Java escapes; no Unicode (for now)
                                    case 'b':  c = '\b'; break;
                                    case 'f':  c = '\f'; break;
                                    case 'n':  c = '\n'; break;
                                    case 'r':  c = '\r'; break;
                                    case 't':  c = '\t'; break;
                                    case '\\': c = '\\'; break;
                                    case '\'': c = '\''; break;
                                    case '\"': c = '\"'; break;
                                    default:
                                        throw new Fault("invalid string constant");
                                }
                            }
                            sb.append(c);
                            if (index < text.length()) {
                                c = text.charAt(index);
                                index++;
                            } else {
                                break;
                            }
                        }
                        if (c == '\"') {
                            token = STRING;
                            idValue = String.valueOf(sb);
                        } else {
                            throw new Fault("invalid string constant");
                        }
                        return;

                    default:
                        if (Character.isUnicodeIdentifierStart(c)) {
                            idValue = String.valueOf(c);
                            while (index < text.length()) {
                                c = text.charAt(index);
                                if (Character.isUnicodeIdentifierPart(c) || c == '.') {
                                    if (!Character.isIdentifierIgnorable(c)) {
                                        idValue += c;
                                    }
                                    index++;
                                } else {
                                    break;
                                }
                            }
                            if (idValue.equalsIgnoreCase("true")) {
                                token = TRUE;
                            } else if (idValue.equalsIgnoreCase("false")) {
                                token = FALSE;
                            } else {
                                token = NAME;
                            }
                            return;
                        } else if (Character.isDigit(c)) {
                            int start = index - 1;
                            while (index < text.length()) {
                                c = text.charAt(index);
                                if (Character.isDigit(c)) {
                                    index++;
                                } else {
                                    switch (c) {
                                        case 'k': case 'K':
                                        case 'm': case 'M':
                                        case 'g': case 'G':
                                            index++;
                                    }
                                    break;
                                }
                            }
                            token = NUMBER;
                            idValue = text.substring(start, index);
                            return;
                        } else {
                            throw new Fault("unrecognized character: `" + c + "'");
                        }
                }
            }
            token = END;
        }

        private final Context context;
        private final String text;
        private int index;
        private Token token;
        private String idValue;
    }

    static enum Token {
        ADD("+"),
        AND("&"),
        DIV("/"),
        END("<end-of-expression>"),
        ERROR("<error>"),
        EQ("="),
        FALSE("false"),
        GE(">="),
        GT(">"),
        LE("<="),
        LPAREN("("),
        LT("<"),
        MATCH("~="),
        MUL("*"),
        NAME("<name>"),
        NE("!="),
        NOT("!"),
        NUMBER("<number>"),
        OR("|"),
        REM("%"),
        RPAREN(")"),
        STRING("<string>"),
        SUB("-"),
        TRUE("true");
        Token(String text) {
            this.text = text;
        }
        String getText() {
            return text.startsWith("<") ? text : "'" + text + "'";
        }
        final String text;
    };

    protected static final int PREC_LIT = 6;
    protected static final int PREC_NOT = 6;
    protected static final int PREC_NUM = 6;
    protected static final int PREC_PRN = 6;
    protected static final int PREC_TRM = 6;
    protected static final int PREC_DIV = 5;
    protected static final int PREC_MUL = 5;
    protected static final int PREC_REM = 5;
    protected static final int PREC_ADD = 4;
    protected static final int PREC_SUB = 4;
    protected static final int PREC_GE = 3;
    protected static final int PREC_GT = 3;
    protected static final int PREC_LE = 3;
    protected static final int PREC_LT = 3;
    protected static final int PREC_EQ = 2;
    protected static final int PREC_NE = 2;
    protected static final int PREC_AND = 1;
    protected static final int PREC_OR = 0;

    //--------------------------------------------------------------------------

    abstract static class BinaryExpr extends Expr {

        BinaryExpr(Expr left, Expr right) {
            this.left = left;
            this.right = right;
        }

        @Override
        Expr order() {
            if (precedence() > left.precedence() && left instanceof BinaryExpr) {
                BinaryExpr e = (BinaryExpr) left;
                left = e.right;
                e.right = order();
                return e;
            } else {
                return this;
            }
        }
        protected Expr left;
        protected Expr right;
    }

    //--------------------------------------------------------------------------

    static class AddExpr extends BinaryExpr {

        AddExpr(Expr left, Expr right) {
            super(left, right);
        }

        public String eval(Context c) throws Fault {
            return String.valueOf(left.evalNumber(c) + right.evalNumber(c));
        }

        int precedence() {
            return PREC_ADD;
        }

        @Override
        public String toString() {
            return "`" + left + "+" + right + "'";
        }
    }

    //--------------------------------------------------------------------------

    static class AndExpr extends BinaryExpr {

        AndExpr(Expr left, Expr right) {
            super(left, right);
        }

        public String eval(Context c) throws Fault {
            return String.valueOf(left.evalBoolean(c) & right.evalBoolean(c));
        }

        int precedence() {
            return PREC_AND;
        }

        @Override
        public String toString() {
            return "`" + left + "&" + right + "'";
        }
    }

    //--------------------------------------------------------------------------

    static class DivideExpr extends BinaryExpr {

        DivideExpr(Expr left, Expr right) {
            super(left, right);
        }

        public String eval(Context c) throws Fault {
            return String.valueOf(left.evalNumber(c) / right.evalNumber(c));
        }

        int precedence() {
            return PREC_DIV;
        }

        @Override
        public String toString() {
            return "`" + left + "/" + right + "'";
        }
    }

    //--------------------------------------------------------------------------

    static class EqualExpr extends BinaryExpr {

        EqualExpr(Expr left, Expr right) {
            super(left, right);
        }

        public String eval(Context c) throws Fault {
            return String.valueOf(left.eval(c).equalsIgnoreCase(right.eval(c)));
        }

        int precedence() {
            return PREC_EQ;
        }

        @Override
        public String toString() {
            return "`" + left + "==" + right + "'";
        }
    }

    //--------------------------------------------------------------------------

    static class GreaterExpr extends BinaryExpr {

        GreaterExpr(Expr left, Expr right) {
            super(left, right);
        }

        public String eval(Context c) throws Fault {
            return String.valueOf(left.evalNumber(c) > right.evalNumber(c));
        }

        int precedence() {
            return PREC_GT;
        }

        @Override
        public String toString() {
            return "`" + left + ">" + right + "'";
        }
    }

    //--------------------------------------------------------------------------

    static class GreaterEqualExpr extends BinaryExpr {

        GreaterEqualExpr(Expr left, Expr right) {
            super(left, right);
        }

        public String eval(Context c) throws Fault {
            return String.valueOf(left.evalNumber(c) >= right.evalNumber(c));
        }

        int precedence() {
            return PREC_GE;
        }

        @Override
        public String toString() {
            return "`" + left + ">=" + right + "'";
        }
    }

    //--------------------------------------------------------------------------

    static class LessExpr extends BinaryExpr {

        LessExpr(Expr left, Expr right) {
            super(left, right);
        }

        public String eval(Context c) throws Fault {
            return String.valueOf(left.evalNumber(c) < right.evalNumber(c));
        }

        int precedence() {
            return PREC_LT;
        }

        @Override
        public String toString() {
            return "`" + left + "<" + right + "'";
        }
    }

    //--------------------------------------------------------------------------

    static class LessEqualExpr extends BinaryExpr {

        LessEqualExpr(Expr left, Expr right) {
            super(left, right);
        }

        public String eval(Context c) throws Fault {
            return String.valueOf(left.evalNumber(c) <= right.evalNumber(c));
        }

        int precedence() {
            return PREC_LE;
        }

        @Override
        public String toString() {
            return "`" + left + "<=" + right + "'";
        }
    }

    //--------------------------------------------------------------------------

    static class MatchExpr extends BinaryExpr {

        MatchExpr(Expr left, Expr right) {
            super(left, right);
        }

        public String eval(Context c) throws Fault {
            return String.valueOf(left.eval(c).matches(right.eval(c)));
        }

        int precedence() {
            return PREC_EQ;
        }

        @Override
        public String toString() {
            return "`" + left + "~=" + right + "'";
        }
    }

    //--------------------------------------------------------------------------

    static class MultiplyExpr extends BinaryExpr {

        MultiplyExpr(Expr left, Expr right) {
            super(left, right);
        }

        public String eval(Context c) throws Fault {
            return String.valueOf(left.evalNumber(c) * right.evalNumber(c));
        }

        int precedence() {
            return PREC_MUL;
        }

        @Override
        public String toString() {
            return "`" + left + "*" + right + "'";
        }
    }

    //--------------------------------------------------------------------------

    static class NameExpr extends Expr {

        NameExpr(String name) {
            this.name = name;
        }

        public String eval(Context c) throws Fault {
            String v = c.get(name);
            if (v == null)
                throw new Fault("name not defined: " + name);
            return v;
        }

        int precedence() {
            return PREC_TRM;
        }

        @Override
        public String toString() {
            return name;
        }

        private final String name;
    }

    //--------------------------------------------------------------------------

    static class NotEqualExpr extends BinaryExpr {

        NotEqualExpr(Expr left, Expr right) {
            super(left, right);
        }

        public String eval(Context c) throws Fault {
            return String.valueOf(!left.eval(c).equalsIgnoreCase(right.eval(c)));
        }

        int precedence() {
            return PREC_NE;
        }

        @Override
        public String toString() {
            return "`" + left + "!=" + right + "'";
        }
    }

    //--------------------------------------------------------------------------

    static class NotExpr extends Expr {

        NotExpr(Expr expr) {
            this.expr = expr;
        }

        public String eval(Context c) throws Fault {
            return String.valueOf(!expr.evalBoolean(c));
        }

        int precedence() {
            return PREC_NOT;
        }

        @Override
        public String toString() {
            return "!" + expr;
        }

        private final Expr expr;
    }

    //--------------------------------------------------------------------------

    static class NumberExpr extends Expr {

        NumberExpr(String value) {
            this.value = value;
        }

        public String eval(Context c) throws Fault {
            long scale;
            char lastCh = value.charAt(value.length() -1);
            switch (lastCh) {
                case 'k': case 'K': scale = 1024; break;
                case 'm': case 'M': scale = 1024 * 1024; break;
                case 'g': case 'G': scale = 1024 * 1024 * 1024; break;
                default:
                    return value;
            }
            try {
                String s = value.substring(0, value.length() - 1);
                return String.valueOf(Long.parseLong(s) * scale);
            } catch (NumberFormatException ex) {
                throw new Fault("invalid numeric value: " + value);
            }
        }

        int precedence() {
            return PREC_NUM;
        }

        @Override
        public String toString() {
            return value;
        }

        private final String value;
    }

    //--------------------------------------------------------------------------

    static class OrExpr extends BinaryExpr {

        OrExpr(Expr left, Expr right) {
            super(left, right);
        }

        public String eval(Context c) throws Fault {
            return String.valueOf(left.evalBoolean(c) | right.evalBoolean(c));
        }

        int precedence() {
            return PREC_OR;
        }

        @Override
        public String toString() {
            return "`" + left + "|" + right + "'";
        }
    }

    //--------------------------------------------------------------------------

    static class ParenExpr extends Expr {

        ParenExpr(Expr expr) {
            this.expr = expr;
        }

        public String eval(Context c) throws Fault {
            return expr.eval(c);
        }

        int precedence() {
            return PREC_PRN;
        }

        @Override
        public String toString() {
            return "(" + expr + ")";
        }

        private final Expr expr;
    }

    //--------------------------------------------------------------------------

    static class RemainderExpr extends BinaryExpr {

        RemainderExpr(Expr left, Expr right) {
            super(left, right);
        }

        public String eval(Context c) throws Fault {
            return String.valueOf(left.evalNumber(c) % right.evalNumber(c));
        }

        int precedence() {
            return PREC_REM;
        }

        @Override
        public String toString() {
            return "`" + left + "%" + right + "'";
        }
    }

    //--------------------------------------------------------------------------

    static class StringExpr extends Expr {

        StringExpr(String value) {
            this.value = value;
        }

        public String eval(Context c) throws Fault {
            return value;
        }

        int precedence() {
            return PREC_LIT;
        }

        @Override
        public String toString() {
            return '"' + value + '"';
        }

        private final String value;
    }

    //--------------------------------------------------------------------------

    static class SubtractExpr extends BinaryExpr {

        SubtractExpr(Expr left, Expr right) {
            super(left, right);
        }

        public String eval(Context c) throws Fault {
            return String.valueOf(left.evalNumber(c) - right.evalNumber(c));
        }

        int precedence() {
            return PREC_SUB;
        }

        @Override
        public String toString() {
            return "`" + left + "-" + right + "'";
        }
    }

}
