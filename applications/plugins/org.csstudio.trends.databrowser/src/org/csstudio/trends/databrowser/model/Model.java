package org.csstudio.trends.databrowser.model;

import java.io.InputStream;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.csstudio.platform.data.ITimestamp;
import org.csstudio.platform.data.TimestampFactory;
import org.csstudio.platform.model.IArchiveDataSource;
import org.csstudio.swt.chart.DefaultColors;
import org.csstudio.swt.chart.TraceType;
import org.csstudio.trends.databrowser.Plugin;
import org.csstudio.trends.databrowser.preferences.Preferences;
import org.csstudio.util.time.RelativeTime;
import org.csstudio.util.time.StartEndTimeParser;
import org.csstudio.util.xml.DOMHelper;
import org.csstudio.util.xml.XMLHelper;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Data model for a chart. 
 *  <p>
 *  Holds a list of PVs, subscribes to new values for those PVs.
 *  <p>
 *  For life values, the model behaves like the EPICS StripTool
 *  (see <a href="http://www.aps.anl.gov/epics">http://www.aps.anl.gov</a>):
 *  While the control system provides good time stamps for the life values,
 *  it is really tricky to use those.
 *  Assume it's 12:00:00, and the last sample we received was stamped
 *  11:59:30. So we draw a plot with line at that (old) value,
 *  using the same value up to 'now', since that's the last we know.
 *  <p>
 *  If the value indeed didn't change, and thus we receive no update, that's OK.
 *  But what if now a network update arrives with a new value, stamped 11:59:58?
 *  The next time we redraw the graph, the value that used to be plotted for the
 *  time 11:59:58 jumps to the newly received value.
 *  That looks very disconcerting (I've tried it in an early EDM xy-chart widget.
 *  Nobody liked it.).
 *  <p>
 *  So this model behaves like StripTool: The network updates for a PV are cached,
 *  i.e. we remember the "most recent" value as the "current" value.
 *  Periodically, the ChartItems are asked to add the current value to their
 *  sequence of samples, using the current host clock as a time stamp.
 *  
 *  @author Kay Kasemir
 */
public class Model
{
    /** If the defaults from prefs aren't usable, this is the start spec. */
    private static final String FALLBACK_START_TIME = "-10 min"; //$NON-NLS-1$

    /** Start- and end time specifications. */
    private StartEndTimeParser start_end_times;
    
    /** Should we scroll or not?
     *  When the end time is absolute, we certainly don't scroll.
     *  But if it's 'now', we can still see that as a one-time
     *  request, or as a continually updated 'now', i.e.
     *  a scrolling mode.
     */
    private boolean scroll;
    
    /** Scan period for 'live' data in seconds. */
    private double scan_period = 0.5;
    
    /** Minimum for scan_period. */
    public static final double MIN_SCAN_RATE = 0.1;
    
    /** Update period of the plot in seconds. */
    private double update_period = 1.0;
    
    /** Minumum for update_period. */
    public static final double MIN_UPDATE_RATE = 0.5;

    /** Ring buffer size, number of elements, for 'live' data. */
    private int ring_size = 1024;

    private ArrayList<AbstractModelItem> items = new ArrayList<AbstractModelItem>();
    
    /** <code>true</code> if all model items were 'started'. */
    private boolean is_running = false;

    private ArrayList<ModelListener> listeners = 
        new ArrayList<ModelListener>();
    
    /** Construct a new model. */
    @SuppressWarnings("nls")
    public Model()
    {
        String start, end;
        try
        {
            start = Preferences.getStartSpecification();
            end = Preferences.getEndSpecification();
        }
        catch (Exception ex)
        {   // No prefs because running as unit test?
            start = FALLBACK_START_TIME;
            end = RelativeTime.NOW;
        }
        try
        {
            // Set time defaults, since otherwise start/end would be null.
            setTimeSpecifications(start, end);
        }
        catch (Exception ex)
        {
            Plugin.logException("Cannot init. time range", ex);
        }
        scroll = start_end_times.isEndNow();
    }
    
    /** Must be called to dispose the model. */
    public void dispose()
    {
        disposeItems();
    }

    /** Peoperly clear the item list. */
    private void disposeItems()
    {
        for (AbstractModelItem item : items)
            item.dispose();
        items.clear();
    }
    
    /** Add a listener. */
    public void addListener(ModelListener listener)
    {
        if (listeners.contains(listener))
            throw new Error("Listener added more than once."); //$NON-NLS-1$
        listeners.add(listener);
    }
    
    /** Remove a listener. */
    public void removeListener(ModelListener listener)
    {
        if (!listeners.contains(listener))
            throw new Error("Unknown listener."); //$NON-NLS-1$
        listeners.remove(listener);
    }

    /** Set a new start and end time specification.
     *  <p>
     *  Also updates the current start and end time with
     *  values computed from the specs "right now".
     *  @see org.csstudio.util.time.StartEndTimeParser
     *  @see #getStartSpecification()
     *  @see #setTimeRange(ITimestamp, ITimestamp)
     *  @exception Exception on parse error of specs.
     */
    public void setTimeSpecifications(String start_specification,
                                      String end_specification) 
        throws Exception
    {
        start_end_times =
            new StartEndTimeParser(start_specification, end_specification);
        final ITimestamp start =
            TimestampFactory.fromCalendar(start_end_times.getStart());
        final ITimestamp end =
            TimestampFactory.fromCalendar(start_end_times.getEnd());
        if (start.isGreaterOrEqual(end))
            start_end_times =
                new StartEndTimeParser(FALLBACK_START_TIME,
                                       RelativeTime.NOW);
        // In case of parse errors, we won't reach this point
        // fireTimeSpecificationsChanged, fireTimeRangeChanged
        for (ModelListener l : listeners)
        {
            l.timeSpecificationsChanged();
            l.timeRangeChanged();
        }
    }
    
    /** Get the start specification that is held 'permamently' when
     *  the model is saved and re-loaded.
     *  <p>
     *  When the specifications are initially loaded or later changed,
     *  the current start and end time is computed from the specs.
     *  <p>
     *  At runtime, scroll operations will update the currently
     *  displayed start and end time by re-evaluating a
     *  relative start specification of for example "-30 min",
     *  but that won't affect the actual start/end specification.
     *  <p>
     *  The config view has buttons to force an update of the specification
     *  from the current start/end times and vice versa.
     *
     *  @see #getStartTime()
     *  @return Start specification.
     */
    public String getStartSpecification()
    {   return start_end_times.getStartSpecification();  }

    /** @see #getStartSpecification()
     *  @return End specification.
     */
    public String getEndSpecification()
    {   return start_end_times.getEndSpecification(); }
    
    /** Re-evaluate the start/end specifications.
     *  <p>
     *  In case of absolute start/end time specs, nothing changes.
     *  For relative start/end time specs, the 'current' start and
     *  end times get updated.
     */
    public void updateStartEndTime()
    {
        try
        {
            if (start_end_times.eval())
            {
                // fireTimeRangeChanged
                for (ModelListener l : listeners)
                    l.timeRangeChanged();
            }
        }
        catch (Exception ex)
        {
            Plugin.logException("Model start/end time update error", ex); //$NON-NLS-1$
        }
    }
    
    /** The start time according to the most recent evaluation
     *  of the start specification.
     *  This is the time where the plot should start.
     *  @see #getStartSpecification()
     *  @return Start time.
     */
    public ITimestamp getStartTime()
    {   return TimestampFactory.fromCalendar(start_end_times.getStart()); }
    
    /** The end time according to the most recent evaluation
     *  of the end specification.
     *  This is the time where the plot should end.
     *  @see #getStartTime()
     *  @return End time.
     */
    public ITimestamp getEndTime()
    {   return TimestampFactory.fromCalendar(start_end_times.getEnd()); }

    /** @return <code>true</code> if the end time is 'now', i.e. we
     *          should continually scroll.
     */
    public boolean isScrollEnabled()
    {
        return scroll;
    }
    
    /** Enable or disable the scroll mode. */
    public void enableScroll(final boolean scroll)
    {
        this.scroll = scroll;
        for (ModelListener listener : listeners)
            listener.timeSpecificationsChanged();
    }
    
    /** @return Returns the scan period in seconds. */
    public double getScanPeriod()
    {   return scan_period; }

    /** @return Returns the update period in seconds. */
    public double getUpdatePeriod()
    {   return update_period; }
    
    /** Set new scan and update periods.
     *  <p>
     *  Actual periods might differ because of enforced minumum etc.
     *
     *  @param scan Scan period in seconds.
     *  @param update Update period in seconds.
     */
    public void setPeriods(double scan, double update)
    {
        // Don't allow 'too fast'
        if (scan < MIN_SCAN_RATE)
            scan = MIN_SCAN_RATE;
        if (update < MIN_UPDATE_RATE)
            update = MIN_UPDATE_RATE;
        // No sense in redrawing faster than the data can change.
        if (update < scan)
            update = scan;
        scan_period = scan;
        update_period = update;
        // firePeriodsChanged
        for (ModelListener l : listeners)
            l.periodsChanged();
    }
    
    /** @return Returns the current ring buffer size. */
    public int getRingSize()
    {   return ring_size; }

    /** @param ring_size The ring_size to set. */
    public void setRingSize(int ring_size)
    {
        this.ring_size = ring_size;
        for (AbstractModelItem item : items)
        {
            if (item instanceof PVModelItem)
                ((PVModelItem)item).setRingSize(ring_size);
        }
    }

    /** @return Returns the number of chart items. */
    public int getNumItems()
    {   return items.size(); }
    
    /** @return Returns the chart item of given index. */
    public IModelItem getItem(int i)
    {   return items.get(i); }

    /** Locate a model item by name.
     *  @param name The PV or formula name to locate.
     *  @return The model item with given name or <code>null</code>.
     */
    public IModelItem findItem(final String name)
    {
        for (IModelItem item : items)
            if (item.getName().equals(name))
                return item;
        return null;
    }
    
    public enum ItemType
    {
        /** A live or archived PV */
        ProcessVariable,
        /** A computed item */
        Formula
    };

    /** Add a new item to the model.
     * 
     *  @param pv_name The PV to add.
     *  @return Returns the newly added chart item.
     */
    public IPVModelItem addPV(String pv_name)
    {
        return (IPVModelItem) add(ItemType.ProcessVariable, pv_name, -1);
    }
    
    /** Add a new item to the model.
     * 
     *  @param pv_name The PV to add.
     *  @param axis_index The Y axis to use [0, 1, ...] or -1 for new axis.
     *  @return Returns the newly added chart item.
     */
    public IPVModelItem addPV(String pv_name, int axis_index)
    {
        return (IPVModelItem) add(ItemType.ProcessVariable, pv_name, axis_index);
    }
    
    /** Add the default archive data sources as per Preferences to item */
    public void addDefaultArchiveSources(IPVModelItem pv_item)
    {
        IArchiveDataSource archives[] = Preferences.getArchiveDataSources();
        for (IArchiveDataSource arch : archives)
            pv_item.addArchiveDataSource(arch);
    }

    /** Add a new item to the model.
     * 
     *  @param type Describes the type of PV to add
     *  @param pv_name The PV to add.
     *  @return Returns the newly added chart item.
     */
    public IModelItem add(ItemType type, String pv_name)
    {
        return add(type, pv_name, -1);
    }

    /** Add a new item to the model.
     *
     *  @param type Describes the type of PV to add
     *  @param pv_name The PV to add.
     *  @param axis_index The Y axis to use [0, 1, ...] or -1 for new axis.
     *  @return Returns the newly added chart item.
     */
    public IModelItem add(ItemType type, String pv_name, int axis_index)
    {
        int c = items.size();
        if (axis_index < 0)
        {
        	axis_index = 0;
            for (int i=0; i<c; ++i)
                if (axis_index < items.get(i).getAxisIndex() + 1)
                    axis_index = items.get(i).getAxisIndex() + 1;
        }
        int line_width = 0;
        return add(type, pv_name, axis_index, DefaultColors.getRed(c),
                DefaultColors.getGreen(c), DefaultColors.getBlue(c),
                line_width);
    }
    
    /** Add a new item to the model.
     * 
     *  @param type Describes the type of PV to add
     *  @param pv_name The PV to add.
     *  @param axis_index The Y axis to use [0, 1, ...]
     *  @param red,
     *  @param green,
     *  @param blue The color to use.
     *  @param line_width The line width.
     *  @return Returns the newly added chart item, or <code>null</code>.
     */
    private IModelItem add(ItemType type, String pv_name, int axis_index,
            int red, int green, int blue, int line_width)
    {
        // Do not allow duplicate PV names.
        int i = findEntry(pv_name);
        if (i >= 0)
            return items.get(i);
        // Default low..high range
        double low = 0.0;
        double high = 10.0;
        final boolean visible = true;
        boolean auto_scale;
        try
        {
            auto_scale = Preferences.getAutoScale();
        }
        catch (Exception ex)
        {   // No prefs because in unit test
            auto_scale = false;
        }
        boolean log_scale = false;
        TraceType trace_type = TraceType.Lines;
        // Use settings of existing item for that axis - if there is one
        for (IModelItem item : items)
            if (item.getAxisIndex() == axis_index)
            {
                low = item.getAxisLow();
                high = item.getAxisHigh();
                auto_scale = item.getAutoScale();
                log_scale = item.getLogScale();
                trace_type = item.getTraceType();
                break;
            }
        AbstractModelItem item = null;
        switch (type)
        {
        case ProcessVariable:
            item = new PVModelItem(this, pv_name, ring_size,
                            		axis_index, low, high, visible, auto_scale,
                                    red, green, blue, line_width, trace_type,
                                    log_scale);
            break;
        case Formula:
            item = new FormulaModelItem(this, pv_name, 
                                    axis_index, low, high, visible, auto_scale,
                                    red, green, blue, line_width, trace_type,
                                    log_scale);
            if (items.size() > 0)
            {  
                // Create a dummy example formula
                // that doubles the first PV
                FormulaInput inputs[] = new FormulaInput[]
                {
                    new FormulaInput(items.get(0), "x") //$NON-NLS-1$
                };
                try
                {
                    ((FormulaModelItem)item).setFormula("2*x", inputs); //$NON-NLS-1$
                }
                catch (Exception ex)
                {
                    Plugin.logException("Setting formula", ex); //$NON-NLS-1$
                }
            }
            break;
        }
        silentAdd(item);
        fireEntryAdded(item);
        return item;
    }

    /** Set axis limits of all items on given axis. */
    public void setAxisLimits(int axis_index, double low, double high)
    {
        for (AbstractModelItem item : items)
        {
            if (item.getAxisIndex() != axis_index)
                continue;
            // Don't call setAxisMin(), Max(), since that would recurse.
            item.setAxisLimitsSilently(low, high);
            fireEntryConfigChanged(item);
        }
    }
    
    /** Set axis type (log, linear) of all items on given axis. */
    void setLogScale(int axis_index, boolean use_log_scale)
    {
        for (AbstractModelItem item : items)
        {
            if (item.getAxisIndex() != axis_index)
                continue;
            if (item.getLogScale() != use_log_scale)
            {
                item.setLogScaleSilently(use_log_scale);
                fireEntryConfigChanged(item);
            }
        }
    }

    /** Set auto scale option of all items on given axis.
     *  <p>
     *  Also updates the auto scaling of all other items on same axis.
     */
    void setAutoScale(int axis_index, boolean use_auto_scale)
    {
        for (AbstractModelItem item : items)
        {
            if (item.getAxisIndex() != axis_index)
                continue;
            if (item.getAutoScale() != use_auto_scale)
            {
                item.setAutoScaleSilently(use_auto_scale);
                fireEntryConfigChanged(item);
            }
        }
    }
    
    /** Add an archive data source to all items in the model.
     *  @see IModelItem#addArchiveDataSource(IArchiveDataSource)
     */
    public void addArchiveDataSource(IArchiveDataSource archive)
    {
        for (IModelItem item : items)
            if (item instanceof IPVModelItem)
                ((IPVModelItem) item).addArchiveDataSource(archive);
    }
    
    /** As <code>add()</code>, but without listener notification.
     *  @see #add()
     */
    private void silentAdd(AbstractModelItem item)
    {
        items.add(item);
        if (is_running  &&  item instanceof PVModelItem)
            ((PVModelItem)item).start();
    }
    
    /** Remove item with given PV name. */
    public void remove(String pv_name)
    {
        int i = findEntry(pv_name);
        if (i < 0)
            return;
        AbstractModelItem item = items.remove(i);
        item.dispose();
        fireEntryRemoved(item);
    }
    
    /** @return Returns index of entry with given PV name or <code>-1</code>. */
    private int findEntry(String pv_name)
    {
        for (int i=0; i<items.size(); ++i)
            if (items.get(i).getName().equals(pv_name))
                return i;
        return -1;
    }
    
    /** @return Returns <code>true</code> if running.
     *  @see #start
     *  @see #stop
     */
    public boolean isRunning()
    {
        return is_running;
    }
    
    /** Start the model (subscribe, ...) */
    public final void start()
    {
        if (!is_running)
        {
            for (AbstractModelItem item : items)
                if (item instanceof PVModelItem)
                    ((PVModelItem)item).start();
            is_running = true;
        }
    }

    /** Stop the model (subscribe, ...) */
    public final void stop()
    {
        if (is_running)
        {
            for (AbstractModelItem item : items)
                if (item instanceof PVModelItem)
                    ((PVModelItem)item).stop();
            is_running = false;
        }
    }
    
    /** Scan PVs. */
    public final void scan()
    {
        final ITimestamp now = TimestampFactory.now();
        for (AbstractModelItem item : items)
            if (item instanceof PVModelItem)
                ((PVModelItem)item).addCurrentValueToSamples(now);
    }

    /** Update (re-compute) formulas. */
    public final void updateFormulas()
    {
        for (AbstractModelItem item : items)
            if (item instanceof FormulaModelItem)
                ((FormulaModelItem)item).compute();
    }
    
    /** @return Returns the whole model as an XML string. */
    @SuppressWarnings("nls")
    public String getXMLContent()
    {
        StringBuffer b = new StringBuffer(1024);
        b.append("<databrowser>\n");
        XMLHelper.XML(b, 1, "start", start_end_times.getStartSpecification());
        XMLHelper.XML(b, 1, "end", start_end_times.getEndSpecification());
        XMLHelper.XML(b, 1, "scroll", Boolean.toString(scroll));
        XMLHelper.XML(b, 1, "scan_period", Double.toString(scan_period));
        XMLHelper.XML(b, 1, "update_period", Double.toString(update_period));
        XMLHelper.XML(b, 1, "ring_size", Integer.toString(ring_size));
        XMLHelper.XML(b, 1, "start", start_end_times.getStartSpecification());
        b.append("    <pvlist>\n");
        for (AbstractModelItem item : items)
            b.append(item.getXMLContent());
        b.append("    </pvlist>\n"); 
        b.append("</databrowser>");
        String s = b.toString();
        return s;
    }
    
    /** Load model from XML file stream. */
    public void load(InputStream stream) throws Exception
    {
        DocumentBuilder docBuilder = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder();
        Document doc = docBuilder.parse(stream);
        loadFromDocument(doc);
    }
    
    /** Load model from DOM document. */
    @SuppressWarnings("nls")
    private void loadFromDocument(Document doc) throws Exception
    {
        final boolean was_running = is_running;
        if (was_running)
            stop();
        disposeItems();

        // Check if it's a <databrowser/>.
        doc.getDocumentElement().normalize();
        final Element root_node = doc.getDocumentElement();
        final String root_name = root_node.getNodeName();
        if (!root_name.equals("databrowser")) 
            throw new Exception("Expected <databrowser>, found <" + root_name
                    + ">");
        // Get the period entries
        String start_specification = DOMHelper.getSubelementString(root_node, "start");
        String end_specification = DOMHelper.getSubelementString(root_node, "end");
        if (start_specification.length() < 1  ||
            end_specification.length() < 1)
        {
            start_specification = Preferences.getStartSpecification();
            end_specification = Preferences.getEndSpecification();
        }
        start_end_times = new StartEndTimeParser(start_specification,
                                                 end_specification);
        scroll = DOMHelper.getSubelementBoolean(root_node, "scroll",
                                                start_end_times.isEndNow());
        final double scan = DOMHelper.getSubelementDouble(root_node, "scan_period");
        final double update = DOMHelper.getSubelementDouble(root_node, "update_period");
        ring_size = DOMHelper.getSubelementInt(root_node, "ring_size");
        Element pvlist = DOMHelper.findFirstElementNode(root_node
                .getFirstChild(), "pvlist");
        if (pvlist != null)
        {
            // Load the PV items
            Element pv = DOMHelper.findFirstElementNode(
            		pvlist.getFirstChild(), PVModelItem.TAG_PV);
            while (pv != null)
            {
                silentAdd(PVModelItem.loadFromDOM(this, pv, ring_size));
                pv = DOMHelper.findNextElementNode(pv, PVModelItem.TAG_PV);
            }
            // Load the Formula items
            pv = DOMHelper.findFirstElementNode(
                    pvlist.getFirstChild(), PVModelItem.TAG_FORMULA);
            while (pv != null)
            {
                silentAdd(FormulaModelItem.loadFromDOM(this, pv));
                pv = DOMHelper.findNextElementNode(pv, PVModelItem.TAG_FORMULA);
            }
        }
        // This also notifies listeners about the new periods:
        setPeriods(scan, update);
        fireEntriesChanged();
        if (was_running)
            start();
    }

    /** @see ModelListener#entryConfigChanged(IModelItem) */
    void fireEntryConfigChanged(IModelItem item)
    {
        for (ModelListener l : listeners)
            l.entryConfigChanged(item);
    }
    
    /** @see ModelListener#entryLookChanged(IModelItem) */
    void fireEntryLookChanged(IModelItem item)
    {
        for (ModelListener l : listeners)
            l.entryLookChanged(item);
    }
    
    /** @see ModelListener#entryArchivesChanged(IModelItem) */
    void fireEntryArchivesChanged(IModelItem item)
    {
        for (ModelListener l : listeners)
            l.entryArchivesChanged(item);
    }

    /** @see ModelListener#entryAdded(IModelItem) */
    void fireEntryAdded(IModelItem item)
    {
        for (ModelListener l : listeners)
            l.entryAdded(item);
    }
        
    /** @see ModelListener#entryRemoved(IModelItem) */
    void fireEntryRemoved(IModelItem item)
    {
        for (ModelListener listener : listeners)
            listener.entryRemoved(item);
    }

    /** @see ModelListener#entriesChanged() */
    private void fireEntriesChanged()
    {
        for (ModelListener l : listeners)
            l.entriesChanged();
    }
}
