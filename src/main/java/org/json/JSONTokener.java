/*
Copyright (c) 2002 JSON.org

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

The Software shall be used for Good, not Evil.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */

package org.json;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;

/**
 * JSONTokener 获取源字符串并从中提取字符和标记（token）。 JSONObject 和 JSONArray 的构造函数使用它来解析 JSON
 * 源字符串。
 * 
 * @author JSON.org
 * @version 2014-05-03
 */
public class JSONTokener {
    /** 当前读取的字符在当前行的位置 */
    private long character;
    /** 输入结束标记 */
    private boolean eof;
    /** 当前读取字符的位置 */
    private long index;
    /** 当前行 */
    private long line;
    /** 前一个字符 */
    private char previous;
    /** 输入读取器 */
    private final Reader reader;
    /** 是否向前回溯字符标记 */
    private boolean usePrevious;
    /** 上一行的字符总数 */
    private long characterPreviousLine;

    /**
     * 从 Reader 构造一个 JSONTokener 。调用者必须关闭该 Reader。
     *
     * @param reader
     *            一个 Reader 对象
     */
    public JSONTokener(Reader reader) {
        this.reader = reader.markSupported() ? reader : new BufferedReader(reader);
        this.eof = false;
        this.usePrevious = false;
        this.previous = 0;
        this.index = 0;
        this.character = 1;
        this.characterPreviousLine = 0;
        this.line = 1;
    }

    /**
     * 从输入流构造一个 JSONTokener 。调用者必须显式关闭流。
     * 
     * @param inputStream
     *            输入流
     */
    public JSONTokener(InputStream inputStream) {
        this(new InputStreamReader(inputStream));
    }

    /**
     * 从字符串构造一个 JSONTokener。
     *
     * @param s
     *            源字符串
     */
    public JSONTokener(String s) {
        this(new StringReader(s));
    }

    /**
     * 备份一个字符。 这样可以在尝试分析下一个数字或标识符之前测试的数字或字母。
     * 
     * @throws JSONException
     *             如果尝试回退超过一步或已经到字符串开始位置抛出异常
     */
    public void back() throws JSONException {
        if (this.usePrevious || this.index <= 0) {
            throw new JSONException("Stepping back two steps is not supported");
        }
        this.decrementIndexes();
        this.usePrevious = true;
        this.eof = false;
    }

    /**
     * 基于之前读取的字符为{@link #back()}方法减少下标值。
     */
    private void decrementIndexes() {
        this.index--;
        if (this.previous == '\r' || this.previous == '\n') {
            this.line--;
            this.character = this.characterPreviousLine;
        } else if (this.character > 0) {
            this.character--;
        }
    }

    /**
     * 获取一个16进制字符对应的十进制数字。
     * 
     * @param c
     *            一个在'0' and '9' 之间或 'A' and 'F'之间或'a' and 'f'之间的字符
     * @return An int between 0 and 15, or -1 if c was not a hex digit.
     */
    public static int dehexchar(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'A' && c <= 'F') {
            return c - ('A' - 10);
        }
        if (c >= 'a' && c <= 'f') {
            return c - ('a' - 10);
        }
        return -1;
    }

    /**
     * 检查是否到达输入的末尾了
     * 
     * @return true：到末尾后不能执行回溯操作了
     */
    public boolean end() {
        return this.eof && !this.usePrevious;
    }

    /**
     * Determine if the source string still contains characters that next() can
     * consume.
     * 
     * @return true if not yet at the end of the source.
     * @throws JSONException
     *             thrown if there is an error stepping forward or backward
     *             while checking for more data.
     */
    public boolean more() throws JSONException {
        if (this.usePrevious) {
            return true;
        }
        try {
            this.reader.mark(1);
        } catch (IOException e) {
            throw new JSONException("Unable to preserve stream position", e);
        }
        try {
            // -1 is EOF, but next() can not consume the null character '\0'
            if (this.reader.read() <= 0) {
                this.eof = true;
                return false;
            }
            this.reader.reset();
        } catch (IOException e) {
            throw new JSONException("Unable to read the next character from the stream", e);
        }
        return true;
    }

    /**
     * 从源字符串中当前位置读取下一个字符。
     *
     * @return 返回下一个字符，或者如果到达源字符串末尾返回0。
     * @throws JSONException
     *             读取源字符串发生异常。
     */
    public char next() throws JSONException {
        int c;
        // 回溯字符
        if (this.usePrevious) {
            // 设置回溯标记为false
            this.usePrevious = false;
            // 设置返回字符为上一个字符
            c = this.previous;
        } else {
            try {
                // 从reader中读取一个字符
                c = this.reader.read();
            } catch (IOException exception) {
                throw new JSONException(exception);
            }
        }
        // 如果到达字符串末尾
        if (c <= 0) { // End of stream
            // 设置结束标记为true
            this.eof = true;
            return 0;
        }
        this.incrementIndexes(c);
        this.previous = (char) c;
        return this.previous;
    }

    /**
     * Increments the internal indexes according to the previous character read
     * and the character passed as the current character.
     * 
     * @param c
     *            the current character read.
     */
    private void incrementIndexes(int c) {
        if (c > 0) {
            this.index++;
            if (c == '\r') {
                this.line++;
                this.characterPreviousLine = this.character;
                this.character = 0;
            } else if (c == '\n') {
                if (this.previous != '\r') {
                    this.line++;
                    this.characterPreviousLine = this.character;
                }
                this.character = 0;
            } else {
                this.character++;
            }
        }
    }

    /**
     * Consume the next character, and check that it matches a specified
     * character.
     * 
     * @param c
     *            The character to match.
     * @return The character.
     * @throws JSONException
     *             if the character does not match.
     */
    public char next(char c) throws JSONException {
        char n = this.next();
        if (n != c) {
            if (n > 0) {
                throw this.syntaxError("Expected '" + c + "' and instead saw '" + n + "'");
            }
            throw this.syntaxError("Expected '" + c + "' and instead saw ''");
        }
        return n;
    }

    /**
     * Get the next n characters.
     *
     * @param n
     *            The number of characters to take.
     * @return A string of n characters.
     * @throws JSONException
     *             Substring bounds error if there are not n characters
     *             remaining in the source string.
     */
    public String next(int n) throws JSONException {
        if (n == 0) {
            return "";
        }

        char[] chars = new char[n];
        int pos = 0;

        while (pos < n) {
            chars[pos] = this.next();
            if (this.end()) {
                throw this.syntaxError("Substring bounds error");
            }
            pos += 1;
        }
        return new String(chars);
    }

    /**
     * 从字符串中读取下一个字符。忽略空白字符。
     * 
     * @throws JSONException
     *             读取源字符串发生错误时抛出异常
     * @return 返回一个字符，如果到字符串末尾了返回0。
     */
    public char nextClean() throws JSONException {
        for (;;) {
            char c = this.next();
            if (c == 0 || c > ' ') {
                return c;
            }
        }
    }

    /**
     * Return the characters up to the next close quote character. Backslash
     * processing is done. The formal JSON format does not allow strings in
     * single quotes, but an implementation is allowed to accept them.
     * 
     * @param quote
     *            The quoting character, either
     *            <code>"</code>&nbsp;<small>(double quote)</small> or
     *            <code>'</code>&nbsp;<small>(single quote)</small>.
     * @return A String.
     * @throws JSONException
     *             Unterminated string.
     */
    public String nextString(char quote) throws JSONException {
        char c;
        StringBuilder sb = new StringBuilder();
        for (;;) {
            c = this.next();
            switch (c) {
            case 0:
            case '\n':
            case '\r':
                throw this.syntaxError("Unterminated string");
            case '\\':
                c = this.next();
                switch (c) {
                case 'b':
                    sb.append('\b');
                    break;
                case 't':
                    sb.append('\t');
                    break;
                case 'n':
                    sb.append('\n');
                    break;
                case 'f':
                    sb.append('\f');
                    break;
                case 'r':
                    sb.append('\r');
                    break;
                case 'u':
                    try {
                        sb.append((char) Integer.parseInt(this.next(4), 16));
                    } catch (NumberFormatException e) {
                        throw this.syntaxError("Illegal escape.", e);
                    }
                    break;
                case '"':
                case '\'':
                case '\\':
                case '/':
                    sb.append(c);
                    break;
                default:
                    throw this.syntaxError("Illegal escape.");
                }
                break;
            default:
                if (c == quote) {
                    return sb.toString();
                }
                sb.append(c);
            }
        }
    }

    /**
     * Get the text up but not including the specified character or the end of
     * line, whichever comes first.
     * 
     * @param delimiter
     *            A delimiter character.
     * @return A string.
     * @throws JSONException
     *             Thrown if there is an error while searching for the delimiter
     */
    public String nextTo(char delimiter) throws JSONException {
        StringBuilder sb = new StringBuilder();
        for (;;) {
            char c = this.next();
            if (c == delimiter || c == 0 || c == '\n' || c == '\r') {
                if (c != 0) {
                    this.back();
                }
                return sb.toString().trim();
            }
            sb.append(c);
        }
    }

    /**
     * Get the text up but not including one of the specified delimiter
     * characters or the end of line, whichever comes first.
     * 
     * @param delimiters
     *            A set of delimiter characters.
     * @return A string, trimmed.
     * @throws JSONException
     *             Thrown if there is an error while searching for the delimiter
     */
    public String nextTo(String delimiters) throws JSONException {
        char c;
        StringBuilder sb = new StringBuilder();
        for (;;) {
            c = this.next();
            if (delimiters.indexOf(c) >= 0 || c == 0 || c == '\n' || c == '\r') {
                if (c != 0) {
                    this.back();
                }
                return sb.toString().trim();
            }
            sb.append(c);
        }
    }

    /**
     * Get the next value. The value can be a Boolean, Double, Integer,
     * JSONArray, JSONObject, Long, or String, or the JSONObject.NULL object.
     * 
     * @throws JSONException
     *             If syntax error.
     *
     * @return An object.
     */
    public Object nextValue() throws JSONException {
        char c = this.nextClean();
        String string;

        switch (c) {
        case '"':
        case '\'':
            return this.nextString(c);
        case '{':
            this.back();
            return new JSONObject(this);
        case '[':
            this.back();
            return new JSONArray(this);
        }

        /*
         * Handle unquoted text. This could be the values true, false, or null,
         * or it can be a number. An implementation (such as this one) is
         * allowed to also accept non-standard forms.
         *
         * Accumulate characters until we reach the end of the text or a
         * formatting character.
         */

        StringBuilder sb = new StringBuilder();
        while (c >= ' ' && ",:]}/\\\"[{;=#".indexOf(c) < 0) {
            sb.append(c);
            c = this.next();
        }
        this.back();

        string = sb.toString().trim();
        if ("".equals(string)) {
            throw this.syntaxError("Missing value");
        }
        return JSONObject.stringToValue(string);
    }

    /**
     * Skip characters until the next character is the requested character. If
     * the requested character is not found, no characters are skipped.
     * 
     * @param to
     *            A character to skip to.
     * @return The requested character, or zero if the requested character is
     *         not found.
     * @throws JSONException
     *             Thrown if there is an error while searching for the to
     *             character
     */
    public char skipTo(char to) throws JSONException {
        char c;
        try {
            long startIndex = this.index;
            long startCharacter = this.character;
            long startLine = this.line;
            this.reader.mark(1000000);
            do {
                c = this.next();
                if (c == 0) {
                    // in some readers, reset() may throw an exception if
                    // the remaining portion of the input is greater than
                    // the mark size (1,000,000 above).
                    this.reader.reset();
                    this.index = startIndex;
                    this.character = startCharacter;
                    this.line = startLine;
                    return 0;
                }
            } while (c != to);
            this.reader.mark(1);
        } catch (IOException exception) {
            throw new JSONException(exception);
        }
        this.back();
        return c;
    }

    /**
     * Make a JSONException to signal a syntax error.
     *
     * @param message
     *            The error message.
     * @return A JSONException object, suitable for throwing
     */
    public JSONException syntaxError(String message) {
        return new JSONException(message + this.toString());
    }

    /**
     * Make a JSONException to signal a syntax error.
     *
     * @param message
     *            The error message.
     * @param causedBy
     *            The throwable that caused the error.
     * @return A JSONException object, suitable for throwing
     */
    public JSONException syntaxError(String message, Throwable causedBy) {
        return new JSONException(message + this.toString(), causedBy);
    }

    /**
     * Make a printable string of this JSONTokener.
     *
     * @return " at {index} [character {character} line {line}]"
     */
    @Override
    public String toString() {
        return " at " + this.index + " [character " + this.character + " line " + this.line + "]";
    }
}
