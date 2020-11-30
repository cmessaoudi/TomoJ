package fr.curie.tomoj.gui;

import fr.curie.gpu.utils.GPUDevice;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;

class GPUDevicesTableModel extends AbstractTableModel {
    String[] columnNames = new String[]{"use", "Device name", "Total memory", "max Ysize for volume", "Ysize used"};
    private Object[][] data;
    private Boolean[] use;
    private Long[] memory;
    private int size;
    GPUDevice[] devices;

    public GPUDevicesTableModel(int width,int height,int depth) {
        devices = GPUDevice.getGPUDevices();
        if (devices == null) return;
        data = new Object[devices.length][columnNames.length];
        use = new Boolean[devices.length];
        memory = new Long[devices.length];
        size = width * depth * 4;
        if (size == 0) size = 512 * 512 * 4;
        for (int i = 0; i < devices.length; i++) {
            use[i] = true;
                /*if(devices[i].getDeviceMaxWidthImage3D()<rec2.getWidth()){
                    IJ.error(devices[i].getDeviceName()+" cannot work with reconstruction of size "+rec2.getWidth() +" (max:"+devices[i].getDeviceMaxWidthImage3D()+")");
                    use[i]=false;
                }*/
            data[i][0] = use[i];
            data[i][1] = devices[i].getDeviceName();
            data[i][2] = devices[i].getDeviceGlobalMemory();
            data[i][3] = computeMaximumSizeForVolume(devices[i]) / size;
            memory[i] = computeMaximumSizeForVolume(devices[i]) / size;
            data[i][4] = Math.min(memory[i], height);
        }
        addTableModelListener(new TableModelListener() {
            public void tableChanged(TableModelEvent e) {
                System.out.println("table changed");
                int row = e.getFirstRow();
                int column = e.getColumn();
                GPUDevicesTableModel model = (GPUDevicesTableModel) e.getSource();
                if (model.getDevices() == null) return;
                String columnName = model.getColumnName(column);
                Object data = model.getValueAt(row, column);

                if (column == 4) devices[row].setOptimumMemoryUse((Long) data * size);
                if (column == 0) {
                    use[row] = model.isGPUSelected(row);

                }

                System.out.println("change data " + columnName + " " + row + "," + column);

            }
        });
    }

    public long computeMaximumSizeForVolume(GPUDevice device) {
        long mem = device.getDeviceGlobalMemory();
        long maxMemAlloc = device.getDeviceMaxAllocationMemory();
        return (long) Math.min(mem * 0.4, maxMemAlloc);
    }

    public void updateValues(int width,int height,int depth) {
        if (devices == null) return;
        //System.out.println("update Table values");
        size = width * depth * 4;
        for (int i = 0; i < devices.length; i++) {
            memory[i] = computeMaximumSizeForVolume(devices[i]) / size;
            setValueAt(memory[i], i, 3);
            //data[i][3] = computeMaximumSizeForVolume(devices[i]) / size;
            if ((Long) data[i][4] > memory[i] || (Long) data[i][4] > height || (Long) data[i][4] == 0) {
                //data[i][4] = Math.min(memory[i], height);
                setValueAt(Math.min(memory[i], height), i, 4);
            }
        }

    }

    public Boolean[] getUse() {
        return use;
    }

    public GPUDevice[] getDevices() {
        return devices;
    }

    public int getRowCount() {
        if (data==null) return 0;
        return data.length;
    }

    public int getColumnCount() {
        return columnNames.length;
    }

    public Object getValueAt(int row, int col) {
        return data[row][col];
    }

    public String getColumnName(int col) {
        return columnNames[col];
    }

    public Class getColumnClass(int c) {
        return getValueAt(0, c).getClass();
    }

    public boolean isGPUSelected(int row) {
        return (Boolean) data[row][0];
    }

    /*
     * Don't need to implement this method unless your table's
     * editable.
     */
    public boolean isCellEditable(int row, int col) {
        if (col == 0) return true;
        if (col == 4) return true;
        return false;
    }

    /*
     * Don't need to implement this method unless your table's
     * data can change.
     */
    public void setValueAt(Object value, int row, int col) {
        data[row][col] = value;
        fireTableCellUpdated(row, col);
    }
} // GPUDevicesTableModel inner class
