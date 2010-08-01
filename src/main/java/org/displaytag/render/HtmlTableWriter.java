/**
 * Licensed under the Artistic License; you may not use this file
 * except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://displaytag.sourceforge.net/license.html
 *
 * THIS PACKAGE IS PROVIDED "AS IS" AND WITHOUT ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, WITHOUT LIMITATION, THE IMPLIED
 * WARRANTIES OF MERCHANTIBILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 */
package org.displaytag.render;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.displaytag.decorator.TableDecorator;
import org.displaytag.exception.DecoratorException;
import org.displaytag.exception.ObjectLookupException;
import org.displaytag.exception.WrappedRuntimeException;
import org.displaytag.model.Column;
import org.displaytag.model.ColumnIterator;
import org.displaytag.model.HeaderCell;
import org.displaytag.model.Row;
import org.displaytag.model.RowIterator;
import org.displaytag.model.TableModel;
import org.displaytag.pagination.PaginatedList;
import org.displaytag.pagination.SmartListHelper;
import org.displaytag.properties.MediaTypeEnum;
import org.displaytag.properties.SortOrderEnum;
import org.displaytag.properties.TableProperties;
import org.displaytag.tags.CaptionTag;
import org.displaytag.tags.TableTagParameters;
import org.displaytag.util.Anchor;
import org.displaytag.util.Href;
import org.displaytag.util.HtmlAttributeMap;
import org.displaytag.util.ParamEncoder;
import org.displaytag.util.PostHref;
import org.displaytag.util.TagConstants;

/**
 * A table writer that formats a table in HTML and writes it to a JSP page.
 * 
 * @author Fabrizio Giustina
 * @author Jorge L. Barroso
 * @version $Id$
 * @see org.displaytag.render.TableWriterTemplate
 * @since 1.1
 */
public class HtmlTableWriter extends TableWriterAdapter {

    /**
     * Logger.
     */
    private static Log log = LogFactory.getLog(HtmlTableWriter.class);

    /**
     * space before any attribute.
     */
    private static final char SPACE = ' ';

    /**
     * <code>TableModel</code>
     */
    private TableModel tableModel;

    /**
     * <code>TableProperties</code>
     */
    private TableProperties properties;

    /**
     * Output destination.
     */
    private JspWriter out;

    /**
     * The param encoder used to generate unique parameter names. Initialized at
     * the first use of encodeParameter().
     */
    private ParamEncoder paramEncoder;

    /**
     * base href used for links.
     */
    private Href baseHref;

    
    private SplitTableHelper splitTableHelper = new SplitTableHelper();
    /**
     * add export links.
     */
    private boolean export;

    private CaptionTag captionTag;
    
    private String script=" <script type='text/javascript'>                                "
                        +" $j(function(){                                                  "
                        +"      var totalwidth = $('statusbar').clientWidth;               "
                        +"      var leftwidth = $('row_left').clientWidth;                 "
                        +"      var centerwidth = $('row_right').clientWidth;               "
                        +"      var rightwidth = totalwidth -leftwidth-centerwidth +'px';   "
                        +"      $('div_center').style.width=rightwidth;                     " 
                        +"                                                                 "
                        +" });                                                             "
                        +" </script>                                                       ";

    /**
     * The paginated list containing the external pagination and sort parameters
     * The presence of this paginated list is what determines if external
     * pagination and sorting is used or not.
     */
    private PaginatedList paginatedList;

    /**
     * Used by various functions when the person wants to do paging.
     */
    private SmartListHelper listHelper;

    /**
     * page size.
     */
    private int pagesize;

    private HtmlAttributeMap attributeMap;

    /**
     * Unique table id.
     */
    private String uid;

    private int[] splitAt;

    /**
     * This table writer uses a <code>TableTag</code> and a
     * <code>JspWriter</code> to do its work.
     * 
     * @param tableTag
     *            <code>TableTag</code> instance called back by this writer.
     * @param out
     *            The output destination.
     */
    public HtmlTableWriter(TableModel tableModel, TableProperties tableProperties, Href baseHref, boolean export, JspWriter out,
            CaptionTag captionTag, PaginatedList paginatedList, SmartListHelper listHelper, int pagesize,
            HtmlAttributeMap attributeMap, String uid) {
        this.tableModel = tableModel;
        this.properties = tableProperties;
        this.baseHref = baseHref;
        this.export = export;
        this.out = out;
        this.captionTag = captionTag;
        this.paginatedList = paginatedList;
        this.listHelper = listHelper;
        this.pagesize = pagesize;
        this.attributeMap = attributeMap;
        this.uid = uid;
    }

    /**
     * Writes a banner containing search result and paging navigation above an
     * HTML table to a JSP page.
     * 
     * @see org.displaytag.render.TableWriterTemplate#writeTopBanner(org.displaytag.model.TableModel)
     */
    protected void writeTopBanner(TableModel model) {
        if (this.tableModel.getForm() != null) {

            String js = "<script type=\"text/javascript\">\n" + "function displaytagform(formname, fields){\n"
                    + "    var objfrm = document.forms[formname];\n"
                    + "    for (j=fields.length-1;j>=0;j--){var f= objfrm.elements[fields[j].f];if (f){f.value=fields[j].v};}\n"
                    + "    objfrm.submit();\n" + "}\n" + "</script>";
            writeFormFields();
            write(js);
        }
        writeSearchResultAndNavigation();
    }

    /**
     * Writes an HTML table's opening tags to a JSP page.
     * 
     * @see org.displaytag.render.TableWriterTemplate#writeTableOpener(org.displaytag.model.TableModel)
     */
    protected void writeTableOpener(TableModel model, String style) {
        this.write(getOpenTag(style));
    }

    // /**
    // * Writes an HTML table's opening tags to a JSP page for split table
    // *
    // * @see
    // org.displaytag.render.TableWriterTemplate#writeTableOpener(org.displaytag.model.TableModel)
    // */
    // protected void writeTableOpener(TableModel model, int width) {
    // this.write(getOpenTag(width));
    // }

    private void writeFormFields() {
        Map parameters = baseHref.getParameterMap();

        ParamEncoder pe = new ParamEncoder(this.tableModel.getId());

        addIfMissing(parameters, pe.encodeParameterName(TableTagParameters.PARAMETER_ORDER));
        addIfMissing(parameters, pe.encodeParameterName(TableTagParameters.PARAMETER_PAGE));
        addIfMissing(parameters, pe.encodeParameterName(TableTagParameters.PARAMETER_SORT));

        for (Iterator it = parameters.keySet().iterator(); it.hasNext();) {
            String key = (String) it.next();
            Object value = parameters.get(key);

            if (value != null & value.getClass().isArray()) {
                Object[] arr = (Object[]) value;
                for (int j = 0; j < arr.length; j++) {
                    writeField(key, arr[j]);
                }
            } else {
                writeField(key, value);
            }
        }
    }

    /**
     * @param key
     * @param value
     */
    private void writeField(String key, Object value) {
        StringBuffer buffer = new StringBuffer();
        buffer.append("<input type=\"hidden\" name=\"");
        buffer.append(esc(key));
        buffer.append("\" value=\"");
        buffer.append(value);
        buffer.append("\"/>");

        write(buffer.toString());
    }

    private String esc(Object value) {
        String valueEscaped = StringUtils.replace(ObjectUtils.toString(value), "\"", "\\\"");
        return valueEscaped;
    }

    /**
     * Adds an element to the given map if empty (use an empty string as value)
     * 
     * @param parameters
     *            Map of parameters
     * @param key
     *            param key
     */
    private void addIfMissing(Map parameters, String key) {
        if (!parameters.containsKey(key)) {
            parameters.put(key, StringUtils.EMPTY);
        }
    }

    /**
     * Writes an HTML table's caption to a JSP page.
     * 
     * @see org.displaytag.render.TableWriterTemplate#writeCaption(org.displaytag.model.TableModel)
     */
    protected void writeCaption(TableModel model) {
        this.write(captionTag.getOpenTag() + model.getCaption() + captionTag.getCloseTag());
    }

    /**
     * Writes an HTML table's footer to a JSP page; HTML requires tfoot to
     * appear before tbody.
     * 
     * @see org.displaytag.render.TableWriterTemplate#writeFooter(org.displaytag.model.TableModel)
     */
    protected void writePreBodyFooter(TableModel model) {
        this.write(TagConstants.TAG_TFOOTER_OPEN);
        this.write(model.getFooter());
        this.write(TagConstants.TAG_TFOOTER_CLOSE);
    }

    /**
     * Writes the start of an HTML table's body to a JSP page.
     * 
     * @see org.displaytag.render.TableWriterTemplate#writeTableBodyOpener(org.displaytag.model.TableModel)
     */
    protected void writeTableBodyOpener(TableModel model) {
        this.write(TagConstants.TAG_TBODY_OPEN);

    }

    /**
     * Writes the end of an HTML table's body to a JSP page.
     * 
     * @see org.displaytag.render.TableWriterTemplate#writeTableBodyCloser(org.displaytag.model.TableModel)
     */
    protected void writeTableBodyCloser(TableModel model) {
        this.write(TagConstants.TAG_TBODY_CLOSE);
    }

    /**
     * Writes the closing structure of an HTML table to a JSP page.
     * 
     * @see org.displaytag.render.TableWriterTemplate#writeTableCloser(org.displaytag.model.TableModel)
     */
    protected void writeTableCloser(TableModel model) {
        this.write(TagConstants.TAG_OPENCLOSING);
        this.write(TagConstants.TABLE_TAG_NAME);
        this.write(TagConstants.TAG_CLOSE);
    }

    /**
     * Writes a banner containing search result, paging navigation, and export
     * links below an HTML table to a JSP page.
     * 
     * @see org.displaytag.render.TableWriterTemplate#writeBottomBanner(org.displaytag.model.TableModel)
     */
    protected void writeBottomBanner(TableModel model) {
        writeNavigationAndExportLinks();
    }

    /**
     * @see org.displaytag.render.TableWriterTemplate#writeDecoratedTableFinish(org.displaytag.model.TableModel)
     */
    protected void writeDecoratedTableFinish(TableModel model) {
        model.getTableDecorator().finish();
    }

    /**
     * @see org.displaytag.render.TableWriterTemplate#writeDecoratedRowStart(org.displaytag.model.TableModel)
     */
    protected void writeDecoratedRowStart(TableModel model) {
        this.write(model.getTableDecorator().startRow());
    }

    /**
     * Writes an HTML table's row-opening tag to a JSP page.
     * 
     * @see org.displaytag.render.TableWriterTemplate#writeRowOpener(org.displaytag.model.Row)
     */
    protected void writeRowOpener(Row row) {
        this.write(row.getOpenTag());
    }

    /**
     * Writes an HTML table's column-opening tag to a JSP page.
     * 
     * @see org.displaytag.render.TableWriterTemplate#writeColumnOpener(org.displaytag.model.Column)
     */
    protected void writeColumnOpener(Column column) throws ObjectLookupException, DecoratorException {
        this.write(column.getOpenTag());
    }

    /**
     * Writes an HTML table's column-closing tag to a JSP page.
     * 
     * @see org.displaytag.render.TableWriterTemplate#writeColumnCloser(org.displaytag.model.Column)
     */
    protected void writeColumnCloser(Column column) {
        this.write(column.getCloseTag());
    }

    /**
     * Writes to a JSP page an HTML table row that has no columns.
     * 
     * @see org.displaytag.render.TableWriterTemplate#writeRowWithNoColumns(java.lang.String)
     */
    protected void writeRowWithNoColumns(String rowValue) {
        this.write(TagConstants.TAG_TD_OPEN);
        this.write(rowValue);
        this.write(TagConstants.TAG_TD_CLOSE);
    }

    /**
     * Writes an HTML table's row-closing tag to a JSP page.
     * 
     * @see org.displaytag.render.TableWriterTemplate#writeRowCloser(org.displaytag.model.Row)
     */
    protected void writeRowCloser(Row row) {
        this.write(row.getCloseTag());
    }

    /**
     * @see org.displaytag.render.TableWriterTemplate#writeDecoratedRowFinish(org.displaytag.model.TableModel)
     */
    protected void writeDecoratedRowFinish(TableModel model) {
        this.write(model.getTableDecorator().finishRow());
    }

    /**
     * Writes an HTML message to a JSP page explaining that the table model
     * contains no data.
     * 
     * @see org.displaytag.render.TableWriterTemplate#writeEmptyListMessage(java.lang.String)
     */
    protected void writeEmptyListMessage(String emptyListMessage) {
        this.write(emptyListMessage);
    }

    /**
     * Writes a HTML table column value to a JSP page.
     * 
     * @see org.displaytag.render.TableWriterTemplate#writeColumnValue(java.lang.String,org.displaytag.model.Column)
     */
    protected void writeColumnValue(Object value, Column column) {
        this.write(value);
    }

    /**
     * Writes an HTML message to a JSP page explaining that the row contains no
     * data.
     * 
     * @see org.displaytag.render.TableWriterTemplate#writeEmptyListRowMessage(java.lang.String)
     */
    protected void writeEmptyListRowMessage(String message) {
        this.write(message);
    }

    /**
     * Writes an HTML table's column header to a JSP page.
     * 
     * @see org.displaytag.render.TableWriterTemplate#writeTableHeader(org.displaytag.model.TableModel)
     */
    protected void writeTableHeader(TableModel model) {

        if (log.isDebugEnabled()) {
            log.debug("[" + tableModel.getId() + "] getTableHeader called");
        }

        // open thead
        write(TagConstants.TAG_THEAD_OPEN);

        // open tr
        write(TagConstants.TAG_TR_OPEN);

        // no columns?
        if (this.tableModel.isEmpty()) {
            write(TagConstants.TAG_TH_OPEN);
            write(TagConstants.TAG_TH_CLOSE);
        }

        // iterator on columns for header
        Iterator iterator = model.getHeaderCellList().iterator();

        while (iterator.hasNext()) {
            // get the header cell
            HeaderCell headerCell = (HeaderCell) iterator.next();

            if (headerCell.getSortable()) {
                String cssSortable = this.properties.getCssSortable();
                headerCell.addHeaderClass(cssSortable);
            }

            // if sorted add styles
            if (headerCell.isAlreadySorted()) {
                // sorted css class
                headerCell.addHeaderClass(this.properties.getCssSorted());

                // sort order css class
                headerCell.addHeaderClass(this.properties.getCssOrder(this.tableModel.isSortOrderAscending()));
            }

            // append th with html attributes
            write(headerCell.getHeaderOpenTag());

            // title
            String header = headerCell.getTitle();

            // column is sortable, create link
            if (headerCell.getSortable()) {
                // creates the link for sorting
                Anchor anchor = new Anchor(getSortingHref(headerCell), header);

                // append to buffer
                header = anchor.toString();
            }

            write(header);
            write(headerCell.getHeaderCloseTag());
        }

        // close tr
        write(TagConstants.TAG_TR_CLOSE);

        // close thead
        write(TagConstants.TAG_THEAD_CLOSE);

        if (log.isDebugEnabled()) {
            log.debug("[" + tableModel.getId() + "] getTableHeader end");
        }
    }

    /**
     * Generates the link to be added to a column header for sorting.
     * 
     * @param headerCell
     *            header cell the link should be added to
     * @return Href for sorting
     */
    private Href getSortingHref(HeaderCell headerCell) {
        // costruct Href from base href, preserving parameters
        Href href = (Href) this.baseHref.clone();

        if (this.tableModel.getForm() != null) {
            href = new PostHref(href, tableModel.getForm());
        }

        if (this.paginatedList == null) {
            // add column number as link parameter
            if (!this.tableModel.isLocalSort() && (headerCell.getSortName() != null)) {
                href.addParameter(encodeParameter(TableTagParameters.PARAMETER_SORT), headerCell.getSortName());
                href.addParameter(encodeParameter(TableTagParameters.PARAMETER_SORTUSINGNAME), "1");
            } else {
                href.addParameter(encodeParameter(TableTagParameters.PARAMETER_SORT), headerCell.getColumnNumber());
            }

            boolean nowOrderAscending = true;

            if (headerCell.getDefaultSortOrder() != null) {
                boolean sortAscending = SortOrderEnum.ASCENDING.equals(headerCell.getDefaultSortOrder());
                nowOrderAscending = headerCell.isAlreadySorted() ? !this.tableModel.isSortOrderAscending() : sortAscending;
            } else {
                nowOrderAscending = !(headerCell.isAlreadySorted() && this.tableModel.isSortOrderAscending());
            }

            int sortOrderParam = nowOrderAscending ? SortOrderEnum.ASCENDING.getCode() : SortOrderEnum.DESCENDING.getCode();
            href.addParameter(encodeParameter(TableTagParameters.PARAMETER_ORDER), sortOrderParam);

            // If user want to sort the full table I need to reset the page
            // number.
            // or if we aren't sorting locally we need to reset the page as
            // well.
            if (this.tableModel.isSortFullTable() || !this.tableModel.isLocalSort()) {
                href.addParameter(encodeParameter(TableTagParameters.PARAMETER_PAGE), 1);
            }
        } else {
            if (properties.getPaginationSkipPageNumberInSort()) {
                href.removeParameter(properties.getPaginationPageNumberParam());
            }

            String sortProperty = headerCell.getSortProperty();
            if (sortProperty == null) {
                sortProperty = headerCell.getBeanPropertyName();
            }

            href.addParameter(properties.getPaginationSortParam(), sortProperty);
            String dirParam;
            if (headerCell.isAlreadySorted()) {
                dirParam = tableModel.isSortOrderAscending() ? properties.getPaginationDescValue() : properties
                        .getPaginationAscValue();
            } else {
                dirParam = properties.getPaginationAscValue();
            }
            href.addParameter(properties.getPaginationSortDirectionParam(), dirParam);
            if (paginatedList.getSearchId() != null) {
                href.addParameter(properties.getPaginationSearchIdParam(), paginatedList.getSearchId());
            }
        }

        return href;
    }

    /**
     * encode a parameter name to be unique in the page using ParamEncoder.
     * 
     * @param parameterName
     *            parameter name to encode
     * @return String encoded parameter name
     */
    private String encodeParameter(String parameterName) {
        // paramEncoder has been already instantiated?
        if (this.paramEncoder == null) {
            // use the id attribute to get the unique identifier
            this.paramEncoder = new ParamEncoder(this.tableModel.getId());
        }

        return this.paramEncoder.encodeParameterName(parameterName);
    }

    /**
     * Generates table footer with links for export commands.
     */
    public void writeNavigationAndExportLinks() {
        // Put the page stuff there if it needs to be there...
        if (this.properties.getAddPagingBannerBottom()) {
            writeSearchResultAndNavigation();
        }

        // add export links (only if the table is not empty)
        if (this.export && this.tableModel.getRowListPage().size() != 0) {
            writeExportLinks();
        }
    }

    /**
     * generates the search result and navigation bar.
     */
    public void writeSearchResultAndNavigation() {
        if ((this.paginatedList == null && this.pagesize != 0 && this.listHelper != null) || (this.paginatedList != null)) {
            // create a new href
            Href navigationHref = (Href) this.baseHref.clone();

            if (tableModel.getForm() != null) {
                navigationHref = new PostHref(navigationHref, tableModel.getForm());
            }

            write(this.listHelper.getSearchResultsSummary());

            String pageParameter;
            if (paginatedList == null) {
                pageParameter = encodeParameter(TableTagParameters.PARAMETER_PAGE);
            } else {
                pageParameter = properties.getPaginationPageNumberParam();
                if ((paginatedList.getSearchId() != null)
                        && (!navigationHref.getParameterMap().containsKey(properties.getPaginationSearchIdParam()))) {
                    navigationHref.addParameter(properties.getPaginationSearchIdParam(), paginatedList.getSearchId());
                }
            }
            write(this.listHelper.getPageNavigationBar(navigationHref, pageParameter));
        }
    }

    /**
     * Writes the formatted export links section.
     */
    private void writeExportLinks() {
        // Figure out what formats they want to export, make up a little string
        Href exportHref = (Href) this.baseHref.clone();

        StringBuffer buffer = new StringBuffer(200);
        Iterator iterator = MediaTypeEnum.iterator();

        while (iterator.hasNext()) {
            MediaTypeEnum currentExportType = (MediaTypeEnum) iterator.next();

            if (this.properties.getAddExport(currentExportType)) {

                if (buffer.length() > 0) {
                    buffer.append(this.properties.getExportBannerSeparator());
                }

                exportHref.addParameter(encodeParameter(TableTagParameters.PARAMETER_EXPORTTYPE), currentExportType.getCode());

                // export marker
                exportHref.addParameter(TableTagParameters.PARAMETER_EXPORTING, "1");

                Anchor anchor = new Anchor(exportHref, this.properties.getExportLabel(currentExportType));
                buffer.append(anchor.toString());
            }
        }

        String[] exportOptions = { buffer.toString() };
        write(MessageFormat.format(this.properties.getExportBanner(), exportOptions));
    }

    /**
     * create the open tag containing all the attributes.
     * 
     * @return open tag string: <code>%lt;table attribute="value" ... ></code>
     */
    public String getOpenTag(String style) {

        if (this.uid != null && attributeMap.get(TagConstants.ATTRIBUTE_ID) == null) {
            // we need to clone the attribute map in order to "fix" the html id
            // when using only the "uid" attribute
            Map localAttributeMap = (Map) attributeMap.clone();
            localAttributeMap.put(TagConstants.ATTRIBUTE_ID, this.uid);

            StringBuffer buffer = new StringBuffer();
            buffer.append(TagConstants.TAG_OPEN).append(TagConstants.TABLE_TAG_NAME);
            buffer.append(localAttributeMap);
            buffer.append(SPACE);
            buffer.append(style);
            buffer.append(SPACE);
            buffer.append(TagConstants.TAG_CLOSE);

            return buffer.toString();

        }

        // fast, no clone
        StringBuffer buffer = new StringBuffer();

        buffer.append(TagConstants.TAG_OPEN).append(TagConstants.TABLE_TAG_NAME);
        buffer.append(attributeMap);
        buffer.append(TagConstants.TAG_CLOSE);

        return buffer.toString();
    }

    /**
     * Utility method.
     * 
     * @param string
     *            String
     */
    public void write(String string) {
        if (string != null) {
            try {
                out.write(string);
            } catch (IOException e) {
                throw new WrappedRuntimeException(getClass(), e);
            }
        }

    }

    public void writeTable(TableModel model, String id) throws JspException {
        /*
         * if attribute splitat is null ,call the
         * TableWriterTemplate.writeTable(TableModel model, String
         * id,HtmlAttributeMap attributeMap)to write the split table
         */
        if (this.attributeMap.get(TagConstants.ATTRIBUTE_SPLITAT) != null) {
            splitAt = splitTableHelper.getSplitAt(model,attributeMap);
            writeSplitTable(model, id);
        } else {
            super.writeTable(model, id);
        }
    }

    /**
     * Utility method.
     * 
     * @param string
     *            String
     */
    public void write(Object string) {
        if (string != null) {
            try {
                out.write(string.toString());
            } catch (IOException e) {
                throw new WrappedRuntimeException(getClass(), e);
            }
        }

    }

    /**
     * Given a table model, this method creates a table, sorting and grouping it
     * per its configuration, while delegating where and how it writes the table
     * to subclass objects. (Background: This method refactors
     * TableTagData.writeHTMLData method. See above.)
     * 
     * @param model
     *            The table model used to build the table.
     * @param id
     *            This table's page id.
     * @throws JspException
     *             if any exception thrown while constructing the tablei, it is
     *             caught and rethrown as a JspException. Extension classes may
     *             throw all sorts of exceptions, depending on their respective
     *             formats and destinations.
     */
    public void writeSplitTable(TableModel model, String id) throws JspException {
        String id1 = "";
        int width_all[];
        int width_left;
        int width_center;
        int width_right;
        TableModel[] tableModels = new TableModel[3];
        tableModels = splitTableHelper.splitTableModel(model,splitAt);
        width_all = splitTableHelper.calculateTableWidth(tableModels[0], splitAt);
        width_left = width_all[0];
        width_center = width_all[1];
        width_right = width_all[2];
        try {
            //write the js to set the rightdiv's width after the whole page rendered
            write(this.script);
            // table id used for logging
            id1 = id;

            TableProperties properties = model.getProperties();

            if (log.isDebugEnabled()) {
                log.debug("[" + id1 + "] writeTable called for table [" + id1 + "]");
            }

            // Handle empty table
            boolean noItems = model.getRowListPage().size() == 0;
            if (noItems && !properties.getEmptyListShowTable()) {
                writeEmptyListMessage(properties.getEmptyListMessage());
                return;
            }

            // Put the page stuff there if it needs to be there...
            if (properties.getAddPagingBannerTop()) {
                // search result and navigation bar
                writeTopBanner(model);
            }
            
            write("<div style='position:absolute'>");

            write("<div id='div_left' style='border: 0px solid red ! important; position:absolute; left:0px; top:0px;overflow-x: hidden; overflow-y: hidden;'>");
            
            // write table_left
            // open table
            this.uid += "_left";
            writeTableOpener(tableModels[0], "style='border-right:0px;width:" + width_left + "px;'");

            // render caption
            if (tableModels[0].getCaption() != null) {
                writeCaption(tableModels[0]);
            }

            // render headers
            if (tableModels[0].getProperties().getShowHeader()) {
                writeTableHeader(tableModels[0]);
            }

            // render footer prior to body
            if (tableModels[0].getFooter() != null) {
                writePreBodyFooter(tableModels[0]);
            }

            // open table body
            this.uid = uid.substring(0, uid.indexOf("_")) + "_center";
            writeTableBodyOpener(tableModels[0]);

            // render table body
            writeTableBody(tableModels[0]);

            // close table body
            writeTableBodyCloser(tableModels[0]);

            // render footer after body
            if (tableModels[0].getFooter() != null) {
                writePostBodyFooter(tableModels[0]);
            }

            // close table
            writeTableCloser(model);

            if (model.getTableDecorator() != null) {
                writeDecoratedTableFinish(model);
            }
            
            write("</div>");
            
            write("<div id='div_center' style='border: 0px solid red ! important; position:absolute; top:0px; left:" + width_left
                    + "px; overflow-x: scroll; overflow-y: hidden;'>");
            
            // write table_center
            // open table
            writeTableOpener(tableModels[1], "style='border-left:0px;width:" + width_center + "px;'");

            // render caption
            if (tableModels[1].getCaption() != null) {
                writeCaption(tableModels[1]);
            }

            // render headers
            if (tableModels[1].getProperties().getShowHeader()) {
                writeTableHeader(tableModels[1]);
            }

            // render footer prior to body
            if (tableModels[1].getFooter() != null) {
                writePreBodyFooter(tableModels[1]);
            }

            // open table body
            writeTableBodyOpener(tableModels[1]);

            // render table body
            writeTableBody(tableModels[1], splitAt[0]);

            // close table body
            writeTableBodyCloser(tableModels[1]);

            // render footer after body
            if (tableModels[1].getFooter() != null) {
                writePostBodyFooter(tableModels[1]);
            }

            // close table
            writeTableCloser(model);

            if (model.getTableDecorator() != null) {
                writeDecoratedTableFinish(model);
            }
            write("</div>");
            
            write("<div id='div_right' style='border: 0px solid red ! important; position:absolute; top:0px; right:0px; overflow-x: scroll; overflow-y: hidden;'>");
            
            // write table_right
            // open table
            writeTableOpener(tableModels[1], "style='border-left:0px;width:" + width_right + "px;'");

            // render caption
            if (tableModels[1].getCaption() != null) {
                writeCaption(tableModels[2]);
            }

            // render headers
            if (tableModels[2].getProperties().getShowHeader()) {
                writeTableHeader(tableModels[2]);
            }

            // render footer prior to body
            if (tableModels[2].getFooter() != null) {
                writePreBodyFooter(tableModels[2]);
            }

            // open table body
            writeTableBodyOpener(tableModels[2]);

            // render table body
            writeTableBody(tableModels[2], splitAt[1]);

            // close table body
            writeTableBodyCloser(tableModels[2]);

            // render footer after body
            if (tableModels[2].getFooter() != null) {
                writePostBodyFooter(tableModels[2]);
            }

            // close table
            writeTableCloser(model);

            if (model.getTableDecorator() != null) {
                writeDecoratedTableFinish(model);
            }
            write("</div>");

            writeBottomBanner(model);

            writeBottomBanner(model);

            write("</div>");

            if (log.isDebugEnabled()) {
                log.debug("[" + id1 + "] writeTable end");
            }
        } catch (Exception e) {
            throw new JspException(e);
        }
    }

    /**
     * Given a table model, writes the table body content, sorting and grouping
     * it per its configuration, while delegating where and how it writes to
     * subclass objects. (Background: This method refactors
     * TableTagData.writeTableBody method. See above.)
     * 
     * @param model
     *            The table model used to build the table body.
     * @throws Exception
     *             if an error is encountered while writing the table body.
     */
    protected void writeTableBody(TableModel model, int splitAt) throws Exception {
        // Ok, start bouncing through our list (only the visible part)
        RowIterator rowIterator = model.getRowIterator(false);

        // iterator on rows
        TableDecorator tableDecorator = model.getTableDecorator();
        Row previousRow = null;
        Row currentRow = null;
        Row nextRow = null;
        Map previousRowValues = new HashMap(10);
        Map currentRowValues = new HashMap(10);
        Map nextRowValues = new HashMap(10);

        while (nextRow != null || rowIterator.hasNext()) {
            // The first pass
            if (currentRow == null) {
                currentRow = rowIterator.next();
            } else {
                previousRow = currentRow;
                currentRow = nextRow;
            }

            if (previousRow != null) {
                previousRowValues.putAll(currentRowValues);
            }
            if (!nextRowValues.isEmpty()) {
                currentRowValues.putAll(nextRowValues);
            }
            // handle the first pass
            else {
                ColumnIterator columnIterator = currentRow.getColumnIterator(model.getHeaderCellList(), splitAt);
                // iterator on columns
                if (log.isDebugEnabled()) {
                    log.debug(" creating ColumnIterator on " + model.getHeaderCellList());
                }
                while (columnIterator.hasNext()) {
                    Column column = columnIterator.nextColumn();

                    // Get the value to be displayed for the column
                    column.initialize();
                    CellStruct struct = new CellStruct(column, column.getChoppedAndLinkedValue());
                    currentRowValues.put(new Integer(column.getHeaderCell().getColumnNumber()), struct);
                }
            }

            nextRowValues.clear();
            // Populate the next row values
            nextRow = rowIterator.hasNext() ? rowIterator.next() : null;
            if (nextRow != null) {
                ColumnIterator columnIterator = nextRow.getColumnIterator(model.getHeaderCellList(), splitAt);
                // iterator on columns
                if (log.isDebugEnabled()) {
                    log.debug(" creating ColumnIterator on " + model.getHeaderCellList());
                }
                while (columnIterator.hasNext()) {
                    Column column = columnIterator.nextColumn();
                    column.initialize();
                    // Get the value to be displayed for the column
                    CellStruct struct = new CellStruct(column, column.getChoppedAndLinkedValue());
                    nextRowValues.put(new Integer(column.getHeaderCell().getColumnNumber()), struct);
                }
            }
            // now we are going to create the current row; reset the decorator
            // to the current row
            if (tableDecorator != null) {
                tableDecorator.initRow(currentRow.getObject(), currentRow.getRowNumber(), currentRow.getRowNumber()
                        + rowIterator.getPageOffset());
            }

            Iterator headerCellsIter = model.getHeaderCellList().iterator();
            ArrayList structsForRow = new ArrayList(model.getHeaderCellList().size());
            lowestEndedGroup = NO_RESET_GROUP;
            lowestStartedGroup = NO_RESET_GROUP;
            while (headerCellsIter.hasNext()) {
                HeaderCell header = (HeaderCell) headerCellsIter.next();

                // Get the value to be displayed for the column
                CellStruct struct = (CellStruct) currentRowValues.get(new Integer(header.getColumnNumber()));
                struct.decoratedValue = struct.bodyValue;
                // Check and see if there is a grouping transition. If there is,
                // then notify the decorator
                if (header.getGroup() != -1) {
                    CellStruct prior = (CellStruct) previousRowValues.get(new Integer(header.getColumnNumber()));
                    CellStruct next = (CellStruct) nextRowValues.get(new Integer(header.getColumnNumber()));
                    // Why npe?
                    String priorBodyValue = prior != null ? prior.bodyValue : null;
                    String nextBodyValue = next != null ? next.bodyValue : null;
                    short groupingValue = groupColumns(struct.bodyValue, priorBodyValue, nextBodyValue, header.getGroup());

                    if (tableDecorator != null) {
                        switch (groupingValue) {
                        case GROUP_START:
                            tableDecorator.startOfGroup(struct.bodyValue, header.getGroup());
                            break;
                        case GROUP_END:
                            tableDecorator.endOfGroup(struct.bodyValue, header.getGroup());
                            break;
                        case GROUP_START_AND_END:
                            tableDecorator.startOfGroup(struct.bodyValue, header.getGroup());
                            tableDecorator.endOfGroup(struct.bodyValue, header.getGroup());
                            break;
                        default:
                            break;
                        }
                    }
                    if (tableDecorator != null) {
                        struct.decoratedValue = tableDecorator.displayGroupedValue(struct.bodyValue, groupingValue, header
                                .getColumnNumber());
                    } else if (groupingValue == GROUP_END || groupingValue == GROUP_NO_CHANGE) {
                        struct.decoratedValue = TagConstants.EMPTY_STRING;
                    }
                }
                structsForRow.add(struct);
            }

            if (tableDecorator != null) {
                writeDecoratedRowStart(model);
            }
            // open row
            writeRowOpener(currentRow);

            for (Iterator iterator = structsForRow.iterator(); iterator.hasNext();) {
                CellStruct struct = (CellStruct) iterator.next();
                writeColumnOpener(struct.column);
                writeColumnValue(struct.decoratedValue, struct.column);
                writeColumnCloser(struct.column);
            }

            if (model.isEmpty()) {
                if (log.isDebugEnabled()) {
                    // log.debug("[" + this.id + "] table has no columns");
                }
                // render empty row
                writeRowWithNoColumns(currentRow.getObject().toString());
            }

            // close row
            writeRowCloser(currentRow);
            // decorate row finish
            if (model.getTableDecorator() != null) {
                writeDecoratedRowFinish(model);
            }
        }

        // render empty list message
        if (model.getRowListPage().size() == 0) {
            writeEmptyListRowMessage(MessageFormat.format(model.getProperties().getEmptyListRowMessage(),
                    new Object[] { new Integer(model.getNumberOfColumns()) }));
        }
    }

    
    
    

}
