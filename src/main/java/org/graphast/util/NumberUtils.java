package org.graphast.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.graphast.exception.GraphastException;

public class NumberUtils {
	/**
     * Returns a numeric value rounded to a specified number of digits.
     * Negative decimals indicates the number of digits to the left of the
     * decimal point to round. For example:
     * <pre>
     * NumberUtils.round(10.4, 0)                // Result: 10.0
     * NumberUtils.round(10.5, 0)                // Result: 11.0
     * NumberUtils.round(-10.5, 0)               // Result: -11.0
     * NumberUtils.round(10.51, 0)               // Result: 11.0
     * NumberUtils.round(10.49999999999999, 2)   // Result: 10.5
     * </pre>
     * Digits between five to nine, inclusive, are rounded up. Digits below
     * five are rounded down.
     * @param number the numeric value to round.
     * @param decimals defines the number of decimal places to retain.
     * @return the numeric value rounded to a specified number of digits.
     */
	public static double round(double number, int decimals) {
        double factor = (int)Math.pow(10, decimals);
        number = number * factor;
        if (number > 0) {
            number = Math.round(number);
        } else {
            number = -Math.round(-number);
        }
        return number / factor;
	}

	/**
     * Returns a numeric value rounded to a specified number of digits.
     * Negative decimals indicates the number of digits to the left of the
     * decimal point to round. For example:
     * <pre>
     * NumberUtils.round(10.4, 0)                // Result: 10.0
     * NumberUtils.round(10.5, 0)                // Result: 11.0
     * NumberUtils.round(-10.5, 0)               // Result: -11.0
     * NumberUtils.round(10.51, 0)               // Result: 11.0
     * NumberUtils.round(10.49999999999999, 2)   // Result: 10.5
     * </pre>
     * Digits between five to nine, inclusive, are rounded up. Digits below
     * five are rounded down.
     * @param number the numeric value to round.
     * @param decimals defines the number of decimal places to retain.
     * @return the numeric value rounded to a specified number of digits.
     */
	public static BigDecimal round(BigDecimal number, int decimals) {
        return number.setScale(decimals, RoundingMode.HALF_UP);
    }
	

	public static long convert(double number, int factor) {
        number = number * factor;
        if (number > 0) {
            number = Math.round(number);
        } else {
            number = -Math.round(-number);
        }
        return (long)number;
	}

	
	public static int convertToInt(Object obj){
		if(obj instanceof Long){
			return (int) ((long) obj);
		}else if(obj instanceof String){
			return Integer.parseInt((String) obj);
		}else{
			throw new GraphastException("Can not convert to int type");
		}
	}
	
}
