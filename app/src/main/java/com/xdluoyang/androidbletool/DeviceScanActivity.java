package com.xdluoyang.androidbletool;

import android.Manifest;
import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
public class DeviceScanActivity extends ListActivity {
    private LeDeviceListAdapter mLeDeviceListAdapter;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothScanner;
    private boolean mScanning;
    private Handler mHandler;

    private static final int REQUEST_ENABLE_BT = 1;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //getActionBar().setTitle(R.string.title_devices);

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        mHandler = new Handler();
        // Initializes list view adapter.
        mLeDeviceListAdapter = new LeDeviceListAdapter();
        setListAdapter(mLeDeviceListAdapter);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                Toast.makeText(this, "请允许定位权限(扫描蓝牙)", Toast.LENGTH_LONG).show();
            } else {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        1);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.i(getLocalClassName(), "request permission result:" + Arrays.toString(grantResults));
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            //ok
        } else {
            Toast.makeText(this, "我们需要此权限来扫描蓝牙设备", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            mBluetoothScanner = mBluetoothAdapter.getBluetoothLeScanner();

            // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
            // fire an intent to display a dialog asking the user to grant permission to enable it.
            if (!mBluetoothAdapter.isEnabled()) {
                if (!mBluetoothAdapter.isEnabled()) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                }
                return;
            }

            scanLeDevice(true);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            Toast.makeText(this, R.string.error_bluetooth_not_open, Toast.LENGTH_SHORT).show();
            finish();
        } else {
            scanLeDevice(true);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        if (!mScanning) {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } else {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(
                    R.layout.actionbar_indeterminate_progress);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan:
                mLeDeviceListAdapter.clear();
                mLeDeviceListAdapter.notifyDataSetChanged();
                scanLeDevice(true);
                break;
            case R.id.menu_stop:
                scanLeDevice(false);
                break;
        }
        return true;
    }


    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
        mLeDeviceListAdapter.clear();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position).getDevice();
        if (device == null) return;
        final Intent intent = new Intent(this, DeviceControlActivity.class);
        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, device.getName());
        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
        if (mScanning) {
            mBluetoothScanner.stopScan(mLeScanCallback);
            mScanning = false;
        }
        startActivity(intent);
    }

    private void scanLeDevice(final boolean enable) {
        if (mBluetoothScanner == null) {
            return;
        }
        if (enable) {
            mHandler.postDelayed(mStopScanRunable, SCAN_PERIOD);
            mScanning = true;
            mBluetoothScanner.startScan(mLeScanCallback);
        } else {
            mHandler.removeCallbacks(mStopScanRunable);
            mScanning = false;
            mBluetoothScanner.stopScan(mLeScanCallback);
        }
        invalidateOptionsMenu();
    }

    // Adapter for holding devices found through scanning.
    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<ScanResult> mLeDevices;
        private LayoutInflater mInflator;

        public LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<>();
            mInflator = DeviceScanActivity.this.getLayoutInflater();
        }

        public void addDevice(ScanResult device) {
            for (ScanResult r : mLeDevices) {
                if (r.getDevice().getAddress().equals(device.getDevice().getAddress())) {
                    Log.i(getLocalClassName(), "the same device in result ignore:" + device.toString());
                    return;
                }
            }

            Log.i(getLocalClassName(), "find device:" + device.toString());
            mLeDevices.add(device);
        }

        public ScanResult getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.item_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = view.findViewById(R.id.device_address);
                viewHolder.deviceName = view.findViewById(R.id.device_name);
                viewHolder.deviceBondState = view.findViewById(R.id.device_bond_state);
                viewHolder.deviceRssi = view.findViewById(R.id.device_rssi);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            ScanResult result = mLeDevices.get(i);
            viewHolder.deviceRssi.setText(String.valueOf(result.getRssi()));

            int state = result.getDevice().getBondState();
            if (state == BluetoothDevice.BOND_BONDED) {
                viewHolder.deviceBondState.setText("bonded");
                viewHolder.deviceBondState.setVisibility(View.VISIBLE);
            } else if (state == BluetoothDevice.BOND_BONDING) {
                viewHolder.deviceBondState.setText("bonding");
                viewHolder.deviceBondState.setVisibility(View.VISIBLE);
            } else {
                viewHolder.deviceBondState.setVisibility(View.GONE);
            }
            BluetoothDevice device = mLeDevices.get(i).getDevice();
            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0)
                viewHolder.deviceName.setText(deviceName);
            else
                viewHolder.deviceName.setText(R.string.unknown_device);
            viewHolder.deviceAddress.setText(device.getAddress());

            return view;
        }
    }

    private Runnable mStopScanRunable = new Runnable() {
        @Override
        public void run() {
            if (mScanning) {
                mScanning = false;
                mBluetoothScanner.stopScan(mLeScanCallback);
                invalidateOptionsMenu();
            }
        }
    };

    // Device scan callback.
    private ScanCallback mLeScanCallback = new ScanCallback() {

        /**
         * Callback when a BLE advertisement has been found.
         *
         * @param callbackType Determines how this callback was triggered. Could be one of
         *            {@link ScanSettings#CALLBACK_TYPE_ALL_MATCHES},
         *            {@link ScanSettings#CALLBACK_TYPE_FIRST_MATCH} or
         *            {@link ScanSettings#CALLBACK_TYPE_MATCH_LOST}
         * @param result A Bluetooth LE scan result.
         */
        public void onScanResult(int callbackType, ScanResult result) {
            runOnUiThread(() -> {
                mLeDeviceListAdapter.addDevice(result);
                mLeDeviceListAdapter.notifyDataSetChanged();
            });
        }

        /**
         * Callback when batch results are delivered.
         *
         * @param results List of scan results that are previously scanned.
         */
        public void onBatchScanResults(List<ScanResult> results) {
        }

        /**
         * Callback when scan could not be started.
         *
         * @param errorCode Error code (one of SCAN_FAILED_*) for scan failure.
         */
        public void onScanFailed(int errorCode) {
        }
    };

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
        TextView deviceBondState;
        TextView deviceRssi;
    }
}