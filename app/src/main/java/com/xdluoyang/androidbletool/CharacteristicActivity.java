package com.xdluoyang.androidbletool;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;

public class CharacteristicActivity extends Activity {

    public static BluetoothGattCharacteristic characteristic;
    public static BluetoothLeService mBluetoothLeService;

    private TextView dataText;
    private boolean isHex = false;


    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                Toast.makeText(CharacteristicActivity.this, "已断开连接", Toast.LENGTH_SHORT).show();
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_characteristic);

        getActionBar().setTitle(characteristic.getUuid().toString()
                .substring(0, characteristic.getUuid().toString().indexOf("-")));
        getActionBar().setDisplayHomeAsUpEnabled(true);

        if (characteristic == null) {
            finish();
            return;
        }

        TextView uuidTxet = findViewById(R.id.uuid_text);
        dataText = findViewById(R.id.data_text);
        uuidTxet.setText(characteristic.getUuid().toString());
        EditText sendEdtx = findViewById(R.id.send_text);
        findViewById(R.id.send_btn).setOnClickListener(v -> {
            if (TextUtils.isEmpty(sendEdtx.getText())) return;
            mBluetoothLeService.writeData(characteristic, sendEdtx.getText().toString().getBytes());
        });


        final int charaProp = characteristic.getProperties();
        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
            // If there is an active notification on a characteristic, clear
            // it first so it doesn't update the data field on the user interface.
            if (mNotifyCharacteristic != null) {
                mBluetoothLeService.setCharacteristicNotification(mNotifyCharacteristic, false);
                mNotifyCharacteristic = null;
            }
            mBluetoothLeService.readCharacteristic(characteristic);
        }

        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
            mNotifyCharacteristic = characteristic;
            mBluetoothLeService.setCharacteristicNotification(characteristic, true);
        }

//      for (BluetoothGattDescriptor d : characteristic.getDescriptors()) {
//          Log.i(TAG, "char:" + characteristic.getUuid().toString() + " descUUID:"
//              + d.getUuid() + " value:" + Arrays.toString(d.getValue()));
//      }
    }


    private void displayData(String data) {
        if (data != null) {
            if (!isHex) {
                dataText.setText(data);
            } else {
                dataText.setText(Arrays.toString(data.getBytes())
                        .substring(1, data.getBytes().length - 1));
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mNotifyCharacteristic != null) {
            mBluetoothLeService.setCharacteristicNotification(mNotifyCharacteristic, false);
            mNotifyCharacteristic = null;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        //runOnUiThread(() -> mConnectionState.setText(resourceId));
    }

    public static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
}
