/*******************************************************************************
 * Copyright (c) 2012 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.csstudio.archive.vtype;

import org.epics.vtype.Display;
import org.epics.vtype.VEnum;
import org.epics.vtype.VNumber;
import org.epics.vtype.VNumberArray;
import org.epics.vtype.VStatistics;
import org.epics.vtype.VString;
import org.epics.vtype.VType;
import org.epics.util.array.ListNumber;

/** Formatter for {@link VType} values
 *  @author Kay Kasemir
 */
@SuppressWarnings("nls")
abstract public class VTypeFormat
{
    /** Number of array elements to show before shortening the printout */
    final public static int MAX_ARRAY_ELEMENTS = 10;
    
    final public static String NOT_A_NUMBER = "NaN";
    final public static String INFINITE = "Inf";
    
    /** Format just the value of a {@link VType} as string (not timestamp, ..)
     *  @param value Value
     *  @return String representation of its value
     */
    public String format(final VType value)
    {
        return format(value, new StringBuilder()).toString();
    }

    /** Format just the value of a {@link VType} into a buffer (not timestamp, ..)
     *  @param value Value
     *  @param buf {@link StringBuilder}
     *  @return {@link StringBuilder}
     */
    public StringBuilder format(final VType value, final StringBuilder buf)
    {
        // After the time this is implemented, VEnum may change into a class
        // that also implements VNumber and/or VString.
        // Handle it first to assert that VEnum is always identified as such
        // and not handled as Number.
        if (value instanceof VEnum)
        {
            final VEnum enumerated = (VEnum)value;
            try
            {
                buf.append(enumerated.getValue());
                buf.append(" (").append(enumerated.getIndex()).append(")");
            }
            catch (ArrayIndexOutOfBoundsException ex)
            {   // Error getting label for invalid index?
                buf.append("<enum ").append(enumerated.getIndex()).append(">");
            }
        }
        else if (value instanceof VNumber)
        {
            final VNumber number = (VNumber) value;
            final Display display = (Display) number;
            format(number.getValue().doubleValue(), display, buf);
        }
        else if (value instanceof VNumberArray)
        {
            final VNumberArray array = (VNumberArray) value;
            final Display display = (Display) array;
            final ListNumber list = array.getData();
            final int N = list.size();
            if (N <= MAX_ARRAY_ELEMENTS)
            {
                if (N > 0)
                    format(list.getDouble(0), display, buf);
                for (int i=1; i<N; ++i)
                {
                    buf.append(", ");
                    format(list.getDouble(i), display, buf);
                }
            }
            else
            {
                format(list.getDouble(0), display, buf);
                for (int i=1; i<MAX_ARRAY_ELEMENTS/2; ++i)
                {
                    buf.append(", ");
                    format(list.getDouble(i), display, buf);
                }
                buf.append(", ... (total ").append(N).append(" elements) ...");
                for (int i = N - MAX_ARRAY_ELEMENTS/2;  i<N;  ++i)
                {
                    buf.append(", ");
                    format(list.getDouble(i), display, buf);
                }
            }
        }
        else if (value instanceof VStatistics)
        {
            final VStatistics stats = (VStatistics) value;
            final Display display = (Display) stats;
            format(stats.getAverage(), display, buf);
            buf.append(" [").append(stats.getMin()).append(" ... ").append(stats.getMax());
            buf.append(", ").append(stats.getNSamples()).append(" samples");
            final Double dev = stats.getStdDev();
            if (dev > 0)
                buf.append(", dev ").append(dev);
            buf.append("]");
        }
        else if (value instanceof VString)
            buf.append(((VString)value).getValue());
        else if (value == null)
            buf.append("null");
        else // TODO: VEnumArray, other types?
            buf.append(value.toString());
        
        return buf;
    }

    /** @param number {@link Number}
     *  @param display {@link Display}
     *  @param buf {@link StringBuilder}
     *  @return {@link StringBuilder}
     */
    abstract public StringBuilder format(final Number number,
            final Display display, final StringBuilder buf);
    
    /** @return Description of the format */
    @Override
    public String toString()
    {
        throw new IllegalStateException("Derived class must override");
    }
}
