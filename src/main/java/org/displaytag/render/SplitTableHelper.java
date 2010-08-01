package org.displaytag.render;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.displaytag.model.Cell;
import org.displaytag.model.HeaderCell;
import org.displaytag.model.Row;
import org.displaytag.model.RowIterator;
import org.displaytag.model.TableModel;
import org.displaytag.util.HtmlAttributeMap;
import org.displaytag.util.TagConstants;

public class SplitTableHelper {

    /**
     * split the TableModel into 3 TableModels according to the attribute
     * splitAt
     * 
     * @param tableModel
     * @return
     */
    public TableModel[] splitTableModel(TableModel tableModel, int[] splitAt) {

        TableModel[] tableModels_splited = new TableModel[3];

        TableModel tableModel_left = (TableModel) (tableModel.clone());
        TableModel tableModel_center = (TableModel) (tableModel.clone());
        TableModel tableModel_right = (TableModel) (tableModel.clone());
        // tableModel中的HeaderCellList需要一分为二·
        List headerCellList_left = sliceList(tableModel_left.getHeaderCellList(), 0, splitAt[0]);
        List headerCellList_center = sliceList(tableModel_center.getHeaderCellList(), splitAt[0], splitAt[1]);
        List headerCellList_right = sliceList(tableModel_center.getHeaderCellList(), splitAt[1], tableModel_center
                .getHeaderCellList().size());
        tableModel_left.setHeaderCellList(headerCellList_left);
        tableModel_center.setHeaderCellList(headerCellList_center);
        tableModel_right.setHeaderCellList(headerCellList_right);
        tableModels_splited[0] = tableModel_left;
        tableModels_splited[1] = tableModel_center;
        tableModels_splited[2] = tableModel_right;
        return tableModels_splited;
    }

    /**
     * use to slice the list
     * 
     * @param list
     * @param begain
     * @param end
     * @return
     */
    public List sliceList(List list, int begain, int end) {
        List retlist = new ArrayList();
        for (int i = begain; i < end; i++) {
            /**
             * if list is a headercelllist,should change the headercell's
             * columnnumber
             */
            if (begain > 0 && list.get(i) instanceof org.displaytag.model.HeaderCell) {
                HeaderCell hc = (HeaderCell) list.get(i);
                hc.setColumnNumber(hc.getColumnNumber() - begain);
            }
            retlist.add(list.get(i));
        }
        return retlist;
    }

    /**
     * calculate the splited table's width
     * 
     * @param left
     *            table's tablemodel
     * @return width of the left table
     */
    public int[] calculateTableWidth(TableModel model, int[] splitAt) {

        int width[] = { 0, 0, 0 };
        Row row = null;
        int loop_flag = 0;
        RowIterator rowIterator = model.getRowIterator(false);
        // get the first row of the table
        if (rowIterator.hasNext()) {
            row = rowIterator.next();
        }

        List cellList = row.getCellList();
        Iterator it = cellList.listIterator();
        int totalWidth = 0;
        while (it.hasNext()) {
            Cell cell = (Cell) it.next();
            String att_width = cell.getPerRowAttributes().get(TagConstants.ATTRIBUTE_STYLE).toString();
            if (att_width.toLowerCase().endsWith("px")) {
                totalWidth += Integer.parseInt(att_width.substring(6, att_width.length() - 2));
            }
            if (loop_flag++ == splitAt[0] - 1) {
                // set the size of left table
                width[0] = totalWidth;
            }else if(loop_flag++ == splitAt[1]-1) {
                width[1] = totalWidth - width[0];
            }
        }
        // set the size of the right table
        width[2] = totalWidth - width[0] - width[1];
        return width;
    }

    /**
     * get the split index from the attributeMap
     * 
     * @param attributeMap
     * @return
     */
    public int[] getSplitAt(TableModel tableModel, HtmlAttributeMap attributeMap) {
        String[] splitAtStr = attributeMap.get(TagConstants.ATTRIBUTE_SPLITAT).toString().split(",");
        int[] splitAtInt = new int[2];
        int totalColumn = tableModel.getHeaderCellList().size();
        splitAtInt[0] = Integer.parseInt(splitAtStr[0]);
        splitAtInt[1] = Integer.parseInt(totalColumn + splitAtStr[1]);
        return splitAtInt;
    }
}
