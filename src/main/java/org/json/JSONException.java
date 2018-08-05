package org.json;

/**
 * 处理出错的时候会抛出 JSONException 异常。
 *
 * @author JSON.org
 * @version 2015-12-09
 */
public class JSONException extends RuntimeException {

    private static final long serialVersionUID = 0;

    /**
     * 使用说明性的信息构造一个 JSONException。
     *
     * @param message
     *            异常的详细原因
     */
    public JSONException(final String message) {
        super(message);
    }

    /**
     * 使用说明性的信息和异常构造一个 JSONException。
     * 
     * @param message
     *            异常的详细原因
     * @param cause
     *            指定的异常
     */
    public JSONException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * 使用指定的异常构造一个 JSONException。
     * 
     * @param cause
     *            指定的异常
     */
    public JSONException(final Throwable cause) {
        super(cause.getMessage(), cause);
    }

}
