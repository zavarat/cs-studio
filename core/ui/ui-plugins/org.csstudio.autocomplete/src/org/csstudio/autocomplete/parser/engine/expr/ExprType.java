/*******************************************************************************
 * This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at 
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     Peter Smith
 *******************************************************************************/
package org.csstudio.autocomplete.parser.engine.expr;

public enum ExprType 
{
    Double,
    Integer,
    Boolean,
    String,
    PV,
    Function,
    Variable,
    Array,
    Expression,
    BinaryOperation,
    ConditionalOperation,
    Missing,
    Error
}
