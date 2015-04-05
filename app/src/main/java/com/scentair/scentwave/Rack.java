package com.scentair.scentwave;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Rack {
    public Integer number;
    public Integer[] phidgetSerialNumbers;
    static public Bay[] bays;

    private static final String db_url = "http://192.168.1.26/dbtest.php";
    private static final String TAG_BAY_STATUS = "bay_status";
    private static final String TAG_BAY_NUMBER = "bay_number";
    private static final String TAG_CALIBRATION_OFFSET = "calibration_offset";
    private static final String TAG_PHIDGET_NUMBER = "phidget_number";
    private static final String TAG_PHIDGET_SERIAL_NUMBER = "phidget_serial_number";

    JSONArray json_operators = null;

    public Rack (Integer number) {
        this.number = number;

        phidgetSerialNumbers = new Integer[3];
        bays = new Bay[24];

        // Get the JSON for the assigned rack
        String url = db_url + "/rack" + number.toString() + "bays";

        JSONParser jParser = new JSONParser();

        json_operators = jParser.getJSONFromUrl(url);

        Boolean status;

        try {
            // looping through all operators
            for (int i = 0; i < json_operators.length(); i++)
            {
                JSONObject q = json_operators.getJSONObject(i);
                String tempStatus = q.getString(TAG_BAY_STATUS);
                if (tempStatus.equals("Active")) status = true;
                 else  status=false;

                Integer offset = q.getInt(TAG_CALIBRATION_OFFSET);
                Integer bayNumber = q.getInt(TAG_BAY_NUMBER);

                Bay newBay = new Bay(bayNumber,status,offset);

                // Storing each json item in variables
                bays[i] = newBay;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        finally {
        }

        // Get the JSON for the assigned rack
        url = db_url + "/rack" + number.toString() + "phidgets";

        jParser = new JSONParser();

        json_operators = jParser.getJSONFromUrl(url);

        try {
            // looping through all operators
            for (int i = 0; i < json_operators.length(); i++)
            {
                JSONObject q = json_operators.getJSONObject(i);

                Integer phidgetNumber = q.getInt(TAG_PHIDGET_NUMBER);
                Integer phidgetSerialNumber = q.getInt(TAG_PHIDGET_SERIAL_NUMBER);

                phidgetSerialNumbers[phidgetNumber-1] = phidgetSerialNumber;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        finally {
        }

    }

    public Bay[] getBays() {
        return bays;
    }
}