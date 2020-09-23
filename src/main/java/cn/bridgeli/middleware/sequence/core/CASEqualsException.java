package cn.bridgeli.middleware.sequence.core;

/**
 * 做CAS原子更新时，如果期望值和更新值相等，则抛出此异常
 *
 * @author bridgeli
 */
public class CASEqualsException extends RuntimeException {
    private static final long serialVersionUID = 374939597531305449L;
}
