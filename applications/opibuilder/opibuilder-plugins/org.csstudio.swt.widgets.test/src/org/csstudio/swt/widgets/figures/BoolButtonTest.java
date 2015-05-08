/*******************************************************************************
 * Copyright (c) 2010 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.csstudio.swt.widgets.figures;
import org.eclipse.draw2d.Figure;


public class BoolButtonTest extends AbstractBoolControlFigureTest{

    @Override
    public Figure createTestWidget() {
        BoolButtonFigure boolButton = new BoolButtonFigure();
        boolButton.setRunMode(true);
        return boolButton;
    }
    
    
    @Override
    public String[] getPropertyNames() {
        String[] superProps =  super.getPropertyNames();
        String[] myProps = new String[]{
                "effect3D",
                "showLED",
                "squareButton"
        };
        
        return concatenateStringArrays(superProps, myProps);
    }
    
    @Override
    public boolean isAutoTest() {
        return true;
    }

        
}
