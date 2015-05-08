/* 
 * Copyright (c) 2008 Stiftung Deutsches Elektronen-Synchrotron, 
 * Member of the Helmholtz Association, (DESY), HAMBURG, GERMANY.
 *
 * THIS SOFTWARE IS PROVIDED UNDER THIS LICENSE ON AN "../AS IS" BASIS. 
 * WITHOUT WARRANTY OF ANY KIND, EXPRESSED OR IMPLIED, INCLUDING BUT NOT LIMITED 
 * TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR PARTICULAR PURPOSE AND 
 * NON-INFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE 
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, 
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR 
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE. SHOULD THE SOFTWARE PROVE DEFECTIVE 
 * IN ANY RESPECT, THE USER ASSUMES THE COST OF ANY NECESSARY SERVICING, REPAIR OR 
 * CORRECTION. THIS DISCLAIMER OF WARRANTY CONSTITUTES AN ESSENTIAL PART OF THIS LICENSE. 
 * NO USE OF ANY SOFTWARE IS AUTHORIZED HEREUNDER EXCEPT UNDER THIS DISCLAIMER.
 * DESY HAS NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, 
 * OR MODIFICATIONS.
 * THE FULL LICENSE SPECIFYING FOR THE SOFTWARE THE REDISTRIBUTION, MODIFICATION, 
 * USAGE AND OTHER RIGHTS AND OBLIGATIONS IS INCLUDED WITH THE DISTRIBUTION OF THIS 
 * PROJECT IN THE FILE LICENSE.HTML. IF THE LICENSE IS NOT INCLUDED YOU MAY FIND A COPY 
 * AT HTTP://WWW.DESY.DE/LEGAL/LICENSE.HTM
 */
 package org.csstudio.sds.ui.internal.properties;

import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.ColorDialog;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

/**
 * A table cell editor for values of type RGB.
 * <p>
 * There is already a ColorCellEditor, but when activated, it adds another step
 * where it only displays a small color patch, the RGB indices and then a button
 * to start the dialog.
 * <p>
 * That's a waste of real estate, adds another 'click' to the editing of colors,
 * plus the overall layout was really poor on Mac OS X, where the button didn't
 * fully show.
 * <p>
 * This implementation, based on the CheckboxCellEditor sources, jumps right
 * into the color dialog.
 * 
 * @author Kay Kasemir
 */
public final class RGBCellEditor extends CellEditor {
    /**
     * A shell.
     */
    private Shell _shell;

    /**
     * The current RGB value.
     */
    private RGB _value;

    /**
     * Creates a new color cell editor parented under the given control. The
     * cell editor value is an SWT RGB value.
     * 
     * @param parent
     *            The parent table.
     */
    public RGBCellEditor(final Composite parent) {
        super(parent, SWT.NONE);
        _shell = parent.getShell();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void activate() {
        ColorDialog dialog = new ColorDialog(_shell);
        if (_value != null) {
            dialog.setRGB(_value);
        }
        _value = dialog.open();
        if (_value != null) {
            fireApplyEditorValue();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Control createControl(final Composite parent) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object doGetValue() {
        return _value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doSetFocus() {
        // Ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doSetValue(final Object value) {
        //Assert.isTrue(value instanceof RGB);
        if (value == null || !(value instanceof RGB)) {
            _value = new RGB(0,0,0);
        } else {
            _value = (RGB) value;
        }
    }
}
