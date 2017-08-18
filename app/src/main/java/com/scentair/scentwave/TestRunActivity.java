package com.scentair.scentwave;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.gson.Gson;
import com.phidgets.Phidget;
import com.phidgets.PhidgetException;
import com.phidgets.event.AttachEvent;
import com.phidgets.event.AttachListener;
import com.phidgets.event.DetachEvent;
import com.phidgets.event.DetachListener;
import com.phidgets.event.SensorChangeEvent;
import com.phidgets.event.SensorChangeListener;
import com.scentair.scentwave.BayItemArrayAdapter.customButtonListener;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Date;

public class TestRunActivity extends Activity implements customButtonListener {
    public static final String TAG_SAVED_TEST_RUN="SAVED_TEST_RUN";
    private String phidgetServerAddress;
    private TestRun testRun;
    private Rack rack;
    private ArrayList<TestStep> testSteps = MainActivity.testSteps.getTestSteps();
    private ListView listView;
    private BayItemArrayAdapter aa;
    private Context context;
    private ArrayList<Failure> failureList;
    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;
    private String currentOperator;
    private boolean highlightFailed = false;
    private Boolean resume = false;
    static public MachineStates machineStates;
    private String showCompleteStepButton = "";
    private Button completeStepButton;
    private Resources resources;
    private static int[] popUpSpeeds;
    // These are used to save the current state of the test run to prefs
    private String testRunSavedState;
    private Gson gson;

    //A timer that posts itself at the end of the runnable
    private Integer LEDBlinkTimer;
    Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            // Toggle the LED for the next bay in sequence on/off
            toggleLED();
            timerHandler.postDelayed(this,LEDBlinkTimer);
        }
    };
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.testrun);
        context = this.getApplicationContext();
        // Initialize the non-volatile storage area
        sharedPreferences = getSharedPreferences(MainActivity.TAG_MYPREFS, Context.MODE_PRIVATE);
        editor = getSharedPreferences(MainActivity.TAG_MYPREFS,Context.MODE_PRIVATE).edit();
        // Check to see if we are supposed to resume a paused/aborted run
        Bundle extras = getIntent().getExtras();
        if (extras!=null) {
            resume=extras.getBoolean(MainActivity.TAG_RESUME_AVAILABLE);
        }
        // Need to make sure we pull out the calibration info before starting the test run
        // Pull the associated rack number from NVM
        currentOperator = sharedPreferences.getString(MainActivity.TAG_OPERATOR_NAME,"");
        phidgetServerAddress = sharedPreferences.getString(MainActivity.TAG_PHIDGET_SERVER_ADDRESS,"192.168.1.22");
        machineStates = new MachineStates();
        resources = getResources();
        popUpSpeeds= resources.getIntArray(R.array.POP_UP_FAN_SPEEDS);
        LEDBlinkTimer = resources.getInteger(R.integer.LED_BLINK_TIMER);

        failureList = MainActivity.failures.getFailures();
        // This will start an async process to load the current rack info from the database
        new loadDBValues().execute("http://this string argument does nothing");
        //Initialize Gson object;
        gson = new Gson();
        showCompleteStepButton="";
        //Need to build out the bay list here.
        //The bay list is a set of fragments attached to a special adapter
        listView = (ListView) findViewById(R.id.list_view);
        listView.setItemsCanFocus(true);
        View footerView =  ((LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.baylistfooter, listView, false);
        listView.addFooterView(footerView);
        completeStepButton = (Button) findViewById(R.id.complete_step_button);
        completeStepButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                completeTestStep();
            }
        });
    }
    @Override
    public void onPassButtonClickListener(int position, int listViewPosition) {
        // One of the test steps has passed for one of the units in the bays
        // Here is where we should:
        //   Update the counts
        //   change the row in some visual way (maybe change to light green background color?
        //   scroll down to the next entry
        //Check to see if they are marking a previous failed unit as passed.
        //if so, clear the data and reset to untested.
        String passStatus = testRun.bayItems[position].isPassReady(testRun.currentTestStep);
        switch ( testRun.bayItems[position].stepStatus) {
            case "Failed":
                // Clear the previous failure cause
                testRun.bayItems[position].failCause = "";
                testRun.bayItems[position].failCauseIndex = 0;
                testRun.bayItems[position].isFailed = false;
                // Reset tested status
                testRun.bayItems[position].stepStatus = "Not Tested";
                testRun.bayItems[position].ledState = "ON";
                updateLED(position,true);
                break;
            case "Passed":
                // The bay has already been passed
                if (testRun.currentTestStep.equals(3)) {
                    // This is a special case.  Requires a dialog popup to pick a value for the fan
                    // noted visually by the operator
                    LayoutInflater inflater = getLayoutInflater();
                    View dialogView = inflater.inflate(R.layout.fanspeedpopup, null);
                    final CharSequence[] popUpSpeedStrings = new CharSequence[popUpSpeeds.length];
                    for (int i = 0; i < popUpSpeeds.length; i++) {
                        Integer popUpSpeed = popUpSpeeds[i];
                        popUpSpeedStrings[i] = popUpSpeed.toString();
                    }
                    final int bayPosition = position;
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Fan Display Value")
                            .setItems(popUpSpeedStrings, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    //Load the proper failure reason into the failure field
                                    testRun.bayItems[bayPosition].fanMedDisplayValue = popUpSpeedStrings[which].toString();
                                    aa.notifyDataSetChanged();
                                }
                            })
                            .setView(dialogView)
                            .show();
                    listView.smoothScrollToPosition(position + 2);
                } else if (testRun.currentTestStep.equals(1)) {
                    // need to clear out the barcodes for the last bay entered
                    clearBarcode();
                }
                break;
            case "Pass":
            case "Not Tested":
                if (testRun.currentTestStep.equals(3)) {
                    // This is a special case.  Requires a dialog popup to pick a value for the fan
                    // noted visually by the operator
                    LayoutInflater inflater = getLayoutInflater();
                    View dialogView = inflater.inflate(R.layout.fanspeedpopup, null);

                    final CharSequence[] popUpSpeedStrings = new CharSequence[popUpSpeeds.length];
                    for (int i = 0; i < popUpSpeeds.length; i++) {
                        Integer popUpSpeed = popUpSpeeds[i];
                        popUpSpeedStrings[i] = popUpSpeed.toString();
                    }
                    final int bayPosition = position;
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Fan Display Value")
                            .setItems(popUpSpeedStrings, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    //Load the proper failure reason into the failure field
                                    testRun.bayItems[bayPosition].fanMedDisplayValue = popUpSpeedStrings[which].toString();
                                    aa.notifyDataSetChanged();
                                }
                            })
                            .setView(dialogView)
                            .show();
                    listView.smoothScrollToPosition(position + 2);
                } else if (testRun.currentTestStep.equals(1)) {
                    // need to clear out the barcodes for the last bay entered
                    clearBarcode();
                }
                // Check the status of the bay.  Is it pass ready?
                if (passStatus.equals("Pass")|| (passStatus.equals("Passed"))) {
                    // They have pressed the pass button, the bay thinks it is pass ready.  Pass it.
                    testRun.bayItems[position].stepStatus = "Passed";
                    listView.smoothScrollToPosition(position+2);
                }
                break;
            case "Barcodes not entered":
                clearBarcode();
                break;
            case "Machine not plugged in":
            case "Fan speeds not recorded":
            default:
                // The pass button should toggle a fail back to not tested.  If the bay has not failed,
                // do nothing.
                break;
        }
        // update results totals
        updateCounts();
    }
    @Override
    public void onFailButtonClickListener(int position, int listViewPosition) {
        // One of the test steps has failed for one of the units in the bays
        // Here is where we should:
        //   Trigger a dialog to tag the failure
        //   Check to see if all units have been tested for this step, if so, start a new step
        //   Update the results totals
        //   change the row in some visual way (maybe change to red background color?
        //   scroll down to the next entry
        if (testRun.bayItems[position].stepStatus.equals("Passed")) {
            // Toggle back to normal state
            testRun.bayItems[position].stepStatus = "Not Tested";
            testRun.bayItems[position].ledState = "ON";
            updateLED(position,true);
        } else {
        // Here we need an AlertDialog that provides a list of potential failure reasons
            TestStep testStep = testSteps.get(testRun.currentTestStep-1);
            testRun.bayItems[position].ledState = "OFF";
            final CharSequence[] failureStrings = new CharSequence[testStep.possibleFailures.size()];
            for (int i=0;i<testStep.possibleFailures.size();i++) {
                Integer failureOffset = testStep.possibleFailures.get(i)-1;
                Failure failure = failureList.get(failureOffset);
                failureStrings[i] = failure.failureText;
            }
            testRun.bayItems[position].stepStatus = "Failed";
            testRun.bayItems[position].isFailed = true;
            testRun.bayItems[position].failStep = testRun.currentTestStep;
            final int bayPosition = position;
            LayoutInflater inflater= getLayoutInflater();
            View dialogView = inflater.inflate(R.layout.failureitem,null);
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Failure Reason")
                    .setItems(failureStrings, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            //Load the proper failure reason into the failure field
                            testRun.bayItems[bayPosition].failCause = failureStrings[which].toString();
                            testRun.bayItems[bayPosition].failCauseIndex = which;
                            aa.notifyDataSetChanged();
                            testRun.bayItems[bayPosition].failStep = testRun.currentTestStep;
                            updateCounts();
                        }
                    })
                    .setView(dialogView)
                    .show();
            listView.smoothScrollToPosition(position+2);
        }
        updateCounts();
    }
    @Override
    public void onScentAirBarCodeClickListener(int position, String candidateText) {
        // Scentair barcode has been entered, need to scroll to the next row
        // skip bays that are inactive
        Integer nextBay = -1;
        testRun.bayItems[position].isEditScentair=false;

        // Added 10/13.  Check to see if the barcode has any '!!!' trash leading.  Sometimes the scanners send trash
        // Remove any of these characters
        String cleanText = candidateText.replaceAll("!(\\S+)","");
        candidateText = cleanText;

        // First, check to see if the barcode is a valid scentair barcode
        // UPDATED 8/18/2017 - JJG
        // Barcode structure has been updated. Now we need to check mitec prefix 'REV' first
        // Then we need to make sure the prefix isn't 'SWD'
        // Then we can assume valid scent air barcode number

        // Check to see if the mitec field is also entered for this position, then move focus to the next row mitec field
        // If the mitec field is not entered, keep the focus on this row.
        if (checkMitecSerialNumber(candidateText)) {
            // This is a valid Mitec barcode.  Put it where it belongs and keep focus here.
            testRun.bayItems[position].mitecBarcode=candidateText;
            nextBay = testRun.setNextBarcodeEditField();
            testRun.bayItems[position].isEditScentair=true;
        }
        else if (checkScentAirSerialNumber(candidateText)) {
            // This is a valid scentair barcode
            // Save it into the array
            testRun.bayItems[position].scentairBarcode=candidateText;
            nextBay = testRun.setNextBarcodeEditField();
            // Make sure this field has lost focus because the barcode is good
            testRun.bayItems[position].isEditScentair=false;
        } else {
            // This is not a valid barcode for either type.  Keep focus here
            testRun.bayItems[position].isEditScentair=true;
        }
        updateCounts();
        if (!nextBay.equals(-1)) {
            listView.setSelection(nextBay);
        }
        aa.notifyDataSetChanged();
    }
    @Override
    public void onMitecBarCodeClickListener(int position, String candidateText) {
        // Something has been entered into the mitec field
        // validate it and enter it if is good.  then move to scentair field
        // need to move focus and cursor to scentair barcode
        Integer nextBay = -1;
        testRun.bayItems[position].isEditMitec=false;

        // Added 10/13.  Check to see if the barcode has any '!!!' trash leading.  Sometimes the scanners send trash
        // Remove any of these characters
        String cleanText = candidateText.replaceAll("!(\\S+)","");
        candidateText = cleanText;

        // First, check to see if the barcode is a valid scentair barcode
        if (checkMitecSerialNumber(candidateText)) {
            // This is a valid mitec barcode
            // Save it into the array
            testRun.bayItems[position].mitecBarcode=candidateText;
            // Check to see if the scentair field is also entered for this position, if not, move focus to that
            // if there is a scentair code already entered, move to next active bay mitec field.
            nextBay = testRun.setNextBarcodeEditField();
            // Make sure this field loses focus since we have a good entry
            testRun.bayItems[position].isEditMitec=false;
        } else if (checkScentAirSerialNumber(candidateText)) {
            // This is a valid scentair barcode.  Put it where it belongs and keep focus here.
            testRun.bayItems[position].scentairBarcode=candidateText;
            nextBay = testRun.setNextBarcodeEditField();
            testRun.bayItems[position].isEditMitec=true;
        } else {
            // This is not a valid barcode for either type.  Keep focus here
            testRun.bayItems[position].isEditMitec=true;
        }
        updateCounts();
        if (!nextBay.equals(-1)) {
            listView.setSelection(nextBay);
        }
        aa.notifyDataSetChanged();
    }
    private boolean checkScentAirSerialNumber(String candidateText) {
        boolean returnValue = false;
        String scentairCheckString = resources.getString(R.string.SCENTWAVEDEVICE_PREFIX);
        // UPDATED 8/18/2017 - JJG
        // They changed the barcode numbering scheme. Scentair barcodes no longer end in .00
        // Now, we need to check to make sure that the prefix for the text isn't 'SWD'
        // If not 'SWD', then assume a valid barcode number
        if (!candidateText.startsWith(scentairCheckString)) {
            // If the string is terminated properly, check that the number of characters is '13'
            if (candidateText.length()>=9) {
                // Barcode ends properly with the correct length.  Looks good.
                returnValue=true;
            }  // Else keep focus here until we get a good value
        }  // else keep focus here until we get a good value
        return returnValue;
    }
    private boolean checkMitecSerialNumber(String candidateText) {
        boolean returnValue = false;
        String mitecCheckString = resources.getString(R.string.MITEC_BARCODE_CHECK);
        //Mitec codes contain the 3 letters "REV" in that order.
        if (candidateText.contains(mitecCheckString)) {
            // Also check the length of the string.  Mitec codes contain 10 characters
            if (candidateText.length()>=10) {
                returnValue=true;
            } // Else keep focus on this field until we get a valid value
        }  // Else keep focus on this field until we get a valid value
        return returnValue;
    }
    private void completeTestStep () {
        // This is the only way to finish this step and move to the next step
        // This button is only active if all active bays have been passed or failed.
        Boolean moveToNextStep = true;
        Boolean showView = false;
        if ((testRun.overallUnitsFailed + testRun.currentStepUnitsFailed)>=testRun.numberOfActiveBays) {
            // This is a special case where all units have failed
            // End the test run here, there is no more to say
            // do not load the next step
            moveToNextStep=false;
            saveTestRunState();
            postTestResults();
            finish();
        } else if ( testRun.currentStepUnitsTested >= testRun.numberOfActiveBays) {
            // There should be at least one unit that is still eligible to pass the run
            // Turn on the Complete Step button.
            moveToNextStep=true;
        } else {
            for (int i = 0; i < rack.numberOfBays; i++) {
                if (testRun.bayItems[i].isActive) {
                    // Only check active bays
                    if (testRun.bayItems[i].stepStatus.equals("Not Tested")) {
                        moveToNextStep = false;
                    }
                }
            }
        }
        if (moveToNextStep) {
            showView=loadNextStep();
        }
        if (showView) updateView();
    }
    protected Boolean loadNextStep() {
        // This is the code to load the next test step and reset the variables for a new run
        // through the bay list
        // Here we need to reset the background colors
        // move the text to the next step
        // reset the various counters
        // go back to the top of the list
        Boolean returnValue=false;
        for (int i=0;i<rack.numberOfBays;i++) {
            // Only check active bays
            if (testRun.bayItems[i].isActive) {
                //If any unit has failed this step, it will be exempt from future steps.
                if (testRun.bayItems[i].stepStatus.equals("Failed")) {
                    //It has failed, skip it for the rest of the run.
                    testRun.bayItems[i].stepStatus = "Failed previous step";
                    testRun.bayItems[i].isFailed = true;
                    testRun.bayItems[i].failStep = testRun.currentTestStep;
                    testRun.overallUnitsFailed++;
                    testRun.bayItems[i].ledState = "OFF";
                } else if (testRun.bayItems[i].stepStatus.equals("Passed")) {
                    testRun.bayItems[i].stepStatus = "Not Tested";
                    testRun.bayItems[i].ledState = "ON";
                }
            } else testRun.bayItems[i].stepStatus = "Inactive";
        }
        //reset counters per step
        testRun.currentStepUnitsFailed = 0;
        testRun.currentStepUnitsPassed = 0;
        testRun.currentStepUnitsTested = 0;
        testRun.currentBay = 0;
        // Turn off complete step button
        showCompleteStepButton="";
        // Update the step complete timestamp
        testRun.testResult.setEndTime(testRun.currentTestStep-1);
        // go to next step
        testRun.currentTestStep++;

        if (testRun.currentTestStep.equals(2)) {
            updatePhidgetSettings();
        }

        // Check if that is the end of the steps and end of this run
        if (testRun.currentTestStep>testRun.maxTestSteps)
        {
            // Stop all blinker timers
            timerHandler.removeCallbacks(timerRunnable);
            // End of this run, report results.
            postTestResults();
            //close this activity
            finish();
        } else {
            // There is at least one more step left in this run
            returnValue=true;
            // Turn the bay lights back on
            for (int i=0;i<rack.numberOfBays;i++) {
                if (testRun.bayItems[i].isActive && !testRun.bayItems[i].isFailed) {
                    if (testRun.currentTestStep.equals(5)) {
                        // Special check to see if the cycle test (step 5) has already passed
                        if (!testRun.bayItems[i].cycleTestComplete) {
                            testRun.bayItems[i].ledState="ON";
                            updateLED(i, true);
                        } else {
                            // The cycle test has already passed, so mark this bay passed
                            testRun.bayItems[i].stepStatus="Passed";
                            // Turn off readings from that bay
                            testRun.currentStepUnitsPassed++;
                        }
                    } else {
                        testRun.bayItems[i].ledState = "ON";
                        updateLED(i, true);
                    }
                }
            }
            //Update the step begun timestamp
            testRun.testResult.setStartTime(testRun.currentTestStep - 1);
            //scroll back to the top of the list
            listView.smoothScrollToPosition(0);

            saveTestRunState();
        }
        return returnValue;
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            for (int i = 0; i < rack.numberOfPhidgetsPerRack; i++) {
                rack.phidgets[i].phidget.close();
            }
        }
        catch (PhidgetException pe) {
            pe.printStackTrace();
        }
        rack = null;
        timerHandler.removeCallbacks(timerRunnable);
    }
    private void updateCounts(){
        testRun.currentStepUnitsFailed=0;
        testRun.currentStepUnitsPassed=0;
        testRun.overallUnitsFailed=0;
        testRun.currentStepUnitsTested=0;
        showCompleteStepButton="";
        // update results totals
        for (int i=0;i<rack.numberOfBays;i++) {
            // Only need to consider active bays
            if (rack.bays[i].active) {
                String returnValue = testRun.bayItems[i].isPassReady(testRun.currentTestStep);
                if (returnValue.equals("Passed")) {
                    testRun.bayItems[i].stepStatus = "Passed";
                    testRun.bayItems[i].ledState = "OFF";
                }
                //count up the numbers of passed and failed units
                if (testRun.bayItems[i].stepStatus.equals("Failed")) {
                    testRun.currentStepUnitsFailed = testRun.currentStepUnitsFailed + 1;
                    testRun.bayItems[i].ledState = "OFF";
                }
                if (testRun.bayItems[i].stepStatus.equals("Passed")) {
                    testRun.currentStepUnitsPassed = testRun.currentStepUnitsPassed + 1;
                    testRun.bayItems[i].ledState = "OFF";
                }
                if (testRun.bayItems[i].stepStatus.equals("Failed previous step")) {
                    testRun.overallUnitsFailed = testRun.overallUnitsFailed + 1;
                    testRun.bayItems[i].ledState = "OFF";
                }
                Boolean turnOn = false;
                if (testRun.bayItems[i].ledState.equals("ON")) turnOn=true;
                updateLED(i, turnOn);
            }
        }
        testRun.currentStepUnitsTested = testRun.currentStepUnitsFailed+testRun.currentStepUnitsPassed+testRun.overallUnitsFailed;
        if ((testRun.overallUnitsFailed>=testRun.numberOfActiveBays) ||
                testRun.currentStepUnitsFailed>=testRun.numberOfActiveBays) {
            // This is a special case where all units have failed
            // End the test run here, there is no more to say
            // do not load the next step
            showCompleteStepButton="FAIL";
            listView.setSelection(aa.getCount()-1);
        } else if ( testRun.currentStepUnitsTested >= testRun.numberOfActiveBays) {
            // There should be at least one unit that is still eligible to pass the run
            // Turn on the Complete Step button.
            showCompleteStepButton="SHOW";
            listView.setSelection(aa.getCount()-1);
        }
        // Only show the view and update saved state if we are not at the end of the run
        updateView();
        saveTestRunState();
    }
    private void updateView(){
        if (testRun!=null) {
            TestStep testStep = testSteps.get(testRun.currentTestStep - 1);
            //Get the header info loaded from the data structure
            TextView currentStep = (TextView) findViewById(R.id.teststepnumber);
            String text = "Step " + testRun.currentTestStep.toString() + " of " + testRun.maxTestSteps.toString();
            currentStep.setText(text);
            //Load the current step start time
            TextView currentStepStartTime = (TextView) findViewById(R.id.start_time);
            SimpleDateFormat format = new SimpleDateFormat("yyyy MM dd hh:mm:ss", Locale.US);
            String dateToStr = format.format(testRun.testResult.getStartTime(testRun.currentTestStep - 1));
            currentStepStartTime.setText(dateToStr);
            //Get the current progress info loaded
            TextView currentProgress = (TextView) findViewById(R.id.test_step_progress);
            Integer baysRemaining = testRun.numberOfActiveBays - testRun.overallUnitsFailed;
            text = "Tested " + testRun.currentStepUnitsTested.toString() + "/" + baysRemaining.toString();
            currentProgress.setText(text);
            TextView activeBays = (TextView) findViewById(R.id.active_bays);
            text = testRun.numberOfActiveBays.toString();
            activeBays.setText(text);
            TextView skippedBaysView = (TextView) findViewById(R.id.skipped_bays);
            Integer skippedBays = testRun.overallUnitsFailed;
            text = skippedBays.toString();
            skippedBaysView.setText(text);
            TextView passedView = (TextView) findViewById(R.id.number_passed);
            text = testRun.currentStepUnitsPassed.toString();
            passedView.setText(text);
            TextView failedView = (TextView) findViewById(R.id.number_failed);
            text = testRun.currentStepUnitsFailed.toString();
            failedView.setText(text);
            if (highlightFailed) {
                failedView.setBackgroundColor(Color.YELLOW);
                highlightFailed = false;
            } else {
                failedView.setBackgroundColor(Color.WHITE);
            }
            //Get the verify list loaded
            TextView verifyText = (TextView) findViewById(R.id.teststepverifylist);
            text = testStep.expectedResults;
            String newLine = System.getProperty("line.separator");
            String newText = text.replaceAll("NEWLINE", newLine);
            verifyText.setSingleLine(false);
            verifyText.setText(newText);
            //Get the test step information from the Test Steps list
            TextView stepInfo = (TextView) findViewById(R.id.teststepinstruction);
            text = testStep.testSteps;
            newText = text.replaceAll("NEWLINE", newLine);
            stepInfo.setSingleLine(false);
            stepInfo.setText(newText);
            if (showCompleteStepButton.equals("SHOW")) {
                // This step is finished
                // Activate the button and set the color and text
                completeStepButton.setBackgroundColor(Color.GREEN);
                completeStepButton.setTextColor(Color.BLACK);
                completeStepButton.setText("Step Complete.  Next Step->");
            } else if (showCompleteStepButton.equals("FAIL")) {
                // This is a failed run.  Indicate jump to post results
                completeStepButton.setBackgroundColor(Color.RED);
                completeStepButton.setTextColor(Color.BLACK);
                completeStepButton.setText("All units failed.  Post Results->");

            } else {
                completeStepButton.setBackgroundColor(Color.GRAY);
                completeStepButton.setTextColor(Color.BLACK);
                Integer baysPending = testRun.numberOfActiveBays - testRun.currentStepUnitsTested;
                String buttonText = "Bays Still Require Testing : " + baysPending;
                completeStepButton.setText(buttonText);
            }
            aa.notifyDataSetChanged();
        }
    }
    private void saveTestRunState () {
        // get test run state info translated to a string
        testRun.currentBay=testRun.currentStepUnitsTested;
        testRunSavedState = gson.toJson(testRun);
        // Added to blink LED on the current bay
        if ( testRun.currentStepUnitsTested < testRun.numberOfActiveBays) {
            // There is at least one bay left to test.
            // Start the timer for 500 milliseconds (set up in the constants value file)
            // The currentBay is the target
            if (testRun.currentTestStep!=5) {
                // We don't need anything to blink on step 5
                // We already have a timer
                // Clean out any old requests so we only have one active at a time
                timerHandler.removeCallbacks(timerRunnable);
                timerHandler.post(timerRunnable);
            } else {
                // test step 5, turn off timer
                // don't want anything to blink anymore
                timerHandler.removeCallbacks(timerRunnable);
            }
        } else timerHandler.removeCallbacks(timerRunnable);
        //update NVM to save state and start on next step on restart/reboot
        editor.putBoolean(MainActivity.TAG_RESUME_AVAILABLE,true);
        editor.putString(TAG_SAVED_TEST_RUN,testRunSavedState);
        editor.commit();
    }
    private void postTestResults() {
        //Calculate results
        testRun.calculateResults(rack);
        // We serialize the test run object to save it here via JSON
        // The run includes the results we just calculated
        testRunSavedState = gson.toJson(testRun);
        // Load up the intent with the data and crank up the new activity
        Intent newIntent = new Intent(context,PostTestResultActivity.class);
        newIntent.putExtra("TestRun",testRunSavedState);
        newIntent.putExtra("RackBays",rack.numberOfBays);
        startActivity(newIntent);
        finish();
    }
    class AttachDetachRunnable implements Runnable {
        Phidget phidget;
        boolean attach;
        public AttachDetachRunnable(Phidget phidget, boolean attach, Boolean startUp)
        {
            this.phidget = phidget;
            this.attach = attach;
            Integer sensorChangeTrigger;
            Integer dataRate;
            Boolean ratioMetric;
            if (startUp) {
                sensorChangeTrigger = resources.getInteger(R.integer.PHIDGET_SENSOR_CHANGE_TRIGGER_START);
                dataRate = resources.getInteger(R.integer.PHIDGET_DATA_RATE_START);
                ratioMetric = resources.getBoolean(R.bool.PHIDGET_RATIO_METRIC);
            } else {
                sensorChangeTrigger = resources.getInteger(R.integer.PHIDGET_SENSOR_CHANGE_TRIGGER);
                dataRate = resources.getInteger(R.integer.PHIDGET_DATA_RATE);
                ratioMetric = resources.getBoolean(R.bool.PHIDGET_RATIO_METRIC);
            }

            try {
                if (phidget.isAttached()) {
                    if (phidget==rack.phidgets[0].phidget) {
                        for (int i = 0; i < 8; i++) {
                            rack.phidgets[0].phidget.setDataRate(i, dataRate);
                            rack.phidgets[0].phidget.setSensorChangeTrigger(i, sensorChangeTrigger);
                        }
                        rack.phidgets[0].phidget.setRatiometric(false);
                    } else if (phidget==rack.phidgets[1].phidget) {
                        for (int i = 0; i < 8; i++) {
                            rack.phidgets[1].phidget.setDataRate(i, dataRate);
                            rack.phidgets[1].phidget.setSensorChangeTrigger(i, sensorChangeTrigger);
                        }
                        rack.phidgets[1].phidget.setRatiometric(false);
                    } else if (phidget==rack.phidgets[2].phidget) {
                        for (int i = 0; i < 8; i++) {
                            rack.phidgets[2].phidget.setDataRate(i, dataRate);
                            rack.phidgets[2].phidget.setSensorChangeTrigger(i, sensorChangeTrigger);
                        }
                        rack.phidgets[2].phidget.setRatiometric(ratioMetric);
                    }
                }
            } catch (PhidgetException pe) {
                pe.printStackTrace();
            }
        }
        public void run() {
            synchronized(this)
            {
                this.notify();
                updateView();
            }
        }
    }
    class SensorChangeRunnable implements Runnable {
        int phidgetNumber,sensorIndex, sensorVal;
        public SensorChangeRunnable(int phidgetNumber, int index, int val) {
            this.sensorIndex = index;
            this.sensorVal = val;
            this.phidgetNumber=phidgetNumber;
        }
        public void run() {
            // Put the value from the phidget into the correct bay
            // after applying the offset
            Integer bayValue = (phidgetNumber*8)+sensorIndex;
            Integer updatedValue=0;
            if (rack.bays!=null) {
                updatedValue = sensorVal + rack.bays[bayValue].calibrationOffset;
            }
            if (testRun!=null) {
                Boolean refreshScreen = testRun.bayItems[bayValue].updateValue(updatedValue,testRun.currentTestStep,context);
                aa.notifyDataSetChanged();
                if (refreshScreen) {
                    updateCounts();
                }
            }
        }
    }
    private void clearBarcode () {
        // Need to find the last bay with a barcode or a partial barcode
        // Then delete the data from that bay and put the focus there.
        Integer nextBay = testRun.setNextBarcodeEditField();
        Integer targetBay;
        // Return cases:
        // All bays are full.  Erase the last bay
        // No bays are full.  Keep cursor at first bay.
        // Bay is partially full.  Erase that bay and keep current location.  Set field to mitec
        // at least one bay is full, current bay is empty.  Erase last bay with data and move to mitec field.

        if (nextBay.equals(-1)) {
            // All bays are full
            // Set the last bay as the target
            targetBay = testRun.bayItems.length - 1;
        } else {
            // Whichever bay returned is what we should look at
            targetBay = nextBay;
            if ( testRun.bayItems[nextBay].mitecBarcode.equals("") && testRun.bayItems[nextBay].scentairBarcode.equals("")) {
                // The current bay is empty, back it up one if possible
                if (!targetBay.equals(0)) {
                    targetBay = targetBay - 1;
                }
            }
        }
        // The first bay is the target bay
        // Set the field to mitec and clear any values already entered
        testRun.bayItems[targetBay].isEditMitec=true;
        testRun.bayItems[targetBay].isEditScentair=false;
        testRun.bayItems[targetBay].scentairBarcode="";
        testRun.bayItems[targetBay].mitecBarcode="";
        testRun.bayItems[targetBay].ledState = "ON";
        testRun.bayItems[targetBay].stepStatus = "Not Tested";
        testRun.setNextBarcodeEditField();

        updateCounts();
        if (!nextBay.equals(-1)) {
            listView.setSelection(nextBay);
        }
        aa.notifyDataSetChanged();
    }

    private void updateLED (Integer bayNumber, Boolean turnOn) {
        // This function figures out the correct phidget and offset, then sets the toggle value
        Integer phidgetOffset = bayNumber/8;
        Integer phidgetSensorNumber = bayNumber - phidgetOffset*8;
        Phidget thisPhidget= rack.phidgets[phidgetOffset].phidget;
        try {
            if(thisPhidget.isAttached()){
                // Perform action on clicks, depending on whether it's now checked
                rack.phidgets[phidgetOffset].phidget.setOutputState(phidgetSensorNumber,turnOn);
            }
        } catch (PhidgetException e) {
            e.printStackTrace();
        }
    }
    private void toggleLED () {
        // Figure out the next active bay
        Integer i=0;
        Boolean exit=false;
        while (!exit) {
            if (i>=rack.numberOfBays) {
                exit = true;
            } else {
                if (testRun.bayItems[i].ledState.equals("ON")) {
                    exit = true;
                } else {
                    i++;
                }
            }
        }
        if (i<rack.numberOfBays) {
            Boolean oldState;
            // This function figures out the correct phidget and offset, then sets the toggle value
            Integer phidgetOffset = i / 8;
            Integer phidgetSensorNumber = i - phidgetOffset * 8;
            Phidget thisPhidget = rack.phidgets[phidgetOffset].phidget;
            try {
                if (thisPhidget.isAttached()) {
                    // Get the old state and flip the toggle
                    oldState = rack.phidgets[phidgetOffset].phidget.getOutputState(phidgetSensorNumber);
                    rack.phidgets[phidgetOffset].phidget.setOutputState(phidgetSensorNumber, !oldState);
                }
            } catch (PhidgetException e) {
                e.printStackTrace();
            }
        }
    }
    private class loadDBValues extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {
            Integer currentRack=sharedPreferences.getInt(MainActivity.TAG_RACK_NUMBER,1);
            rack = new Rack(currentRack,MainActivity.dbServerAddress);
            return urls[0];
        }
        @Override
        protected void onPostExecute(String result) {
            // Continue setup after we have loaded the rack info from the DB.
            Boolean firstStart=true;
            if (rack!=null) {
                // The rack null check is for when you try to start without being connected
                testRun = new TestRun();
                if (resume) {
                    // Get the saved test run from prefs to start on from preferences
                    testRunSavedState = sharedPreferences.getString(TAG_SAVED_TEST_RUN, "");
                    testRun = gson.fromJson(testRunSavedState,TestRun.class);

                    // Need to check if past step 1.  If so, update the phidget settings.
                    if (testRun.currentTestStep>1) {
                        firstStart=false;
                    }

                    // If we are in step 5, need to clear out the target cycle times since they no longer apply on resume
                    if (testRun.currentTestStep.equals(5)) {
                        for (int i = 0; i < rack.numberOfBays; i++) {
                            if ((testRun.bayItems[i].isActive) &&
                                    (!testRun.bayItems[i].isFailed) &&
                                    (!testRun.bayItems[i].cycleTestComplete)) {
                                // Bay is active, bay still has not passed or failed test yet.
                                // Reset any timer targets
                                testRun.bayItems[i].lastOffTime = new Date();
                            }
                        }
                    }
                    Toast.makeText(getApplicationContext(), "Test Run Resumed", Toast.LENGTH_LONG).show();
                }
                else {
                    //Initialize this test run
                    testRun = new TestRun(currentOperator, rack,testSteps.size());
                    // Make sure we read the test steps from the proper data structure
                    // Reset resume status and clear saved test run
                    editor.putBoolean(MainActivity.TAG_RESUME_AVAILABLE, false);
                    editor.putString(TAG_SAVED_TEST_RUN,"");
                    editor.commit();
                    // DEBUG - check if barcode debug is set.  If so, through some junk into the barcodes
                    // so we can move on to the rest of the tests
                    Boolean barcodeOverride = sharedPreferences.getBoolean(MainActivity.DEBUG_BARCODE_OVERRIDE, false);
                    if (barcodeOverride) {
                        // Mitec barcodes are set to letters, scentair to numbers
                        testRun.bayItems[0].mitecBarcode="AAA123";
                        testRun.bayItems[0].scentairBarcode="123AAA";
                        testRun.bayItems[1].mitecBarcode="AAA123333";
                        testRun.bayItems[1].scentairBarcode="124AAA";
                        testRun.bayItems[2].mitecBarcode="AAA128";
                        testRun.bayItems[2].scentairBarcode="127AAA";
                        testRun.bayItems[3].mitecBarcode="AAA123111";
                        testRun.bayItems[3].scentairBarcode="122111AAA";
                        testRun.bayItems[4].mitecBarcode="AAA124";
                        testRun.bayItems[4].scentairBarcode="123AAA";
                        testRun.bayItems[5].mitecBarcode="AAA122";
                        testRun.bayItems[5].scentairBarcode="121AAA";
                        testRun.bayItems[6].mitecBarcode="AAA121";
                        testRun.bayItems[6].scentairBarcode="121AAA";
                        testRun.bayItems[7].mitecBarcode="AAA128";
                        testRun.bayItems[7].scentairBarcode="1212AAA";
                        testRun.bayItems[8].mitecBarcode="AAA1123";
                        testRun.bayItems[8].scentairBarcode="12352AAA";
                        testRun.bayItems[9].mitecBarcode="AAA1252";
                        testRun.bayItems[9].scentairBarcode="1231245AAA";
                        testRun.bayItems[10].mitecBarcode="AAA1256123";
                        testRun.bayItems[10].scentairBarcode="12312AAA";
                        testRun.bayItems[11].mitecBarcode="AA12A123";
                        testRun.bayItems[11].scentairBarcode="12351A2AA";
                        testRun.bayItems[12].mitecBarcode="AA12A11223";
                        testRun.bayItems[12].scentairBarcode="123A12A1A";
                        testRun.bayItems[13].mitecBarcode="AA11A12123";
                        testRun.bayItems[13].scentairBarcode="1213A1A23A";
                        testRun.bayItems[14].mitecBarcode="AA215A1123";
                        testRun.bayItems[14].scentairBarcode="123AA215A";
                        testRun.bayItems[15].mitecBarcode="AAA122213";
                        testRun.bayItems[15].scentairBarcode="1232A1A2A";
                        testRun.bayItems[16].mitecBarcode="AA2A12253";
                        testRun.bayItems[16].scentairBarcode="12232A1A1A";
                        testRun.bayItems[17].mitecBarcode="A6AA123";
                        testRun.bayItems[17].scentairBarcode="12993AAA";
                        testRun.bayItems[18].mitecBarcode="A4AA123";
                        testRun.bayItems[18].scentairBarcode="1263453A4AA";
                        testRun.bayItems[19].mitecBarcode="A4AA41523";
                        testRun.bayItems[19].scentairBarcode="123A7A43A";
                        testRun.bayItems[20].mitecBarcode="A333AA123";
                        testRun.bayItems[20].scentairBarcode="12333AA3A";
                        testRun.bayItems[21].mitecBarcode="A888AA3123";
                        testRun.bayItems[21].scentairBarcode="132436AA33A";
                        testRun.bayItems[22].mitecBarcode="A2345AA123";
                        testRun.bayItems[22].scentairBarcode="123AA2344A";
                        testRun.bayItems[23].mitecBarcode="A3A33A2123";
                        testRun.bayItems[23].scentairBarcode="1223453A5A5A";
                    }
                }
                aa= new BayItemArrayAdapter(context, testRun);
                aa.setCustomButtonListener(TestRunActivity.this);

                // need to initialize the phidgets to control the LED lights
                try {
                    final Boolean startUp = firstStart;
                    for (int i=0;i<rack.numberOfPhidgetsPerRack;i++) {
                        rack.phidgets[i].phidget.addAttachListener(new AttachListener() {
                            public void attached(final AttachEvent ae) {
                                AttachDetachRunnable handler = new AttachDetachRunnable(ae.getSource(), true, startUp);
                                synchronized (handler) {
                                    runOnUiThread(handler);
                                    try {
                                        handler.wait();
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        });
                        rack.phidgets[i].phidget.addDetachListener(new DetachListener() {
                            public void detached(final DetachEvent ae) {
                                AttachDetachRunnable handler = new AttachDetachRunnable(ae.getSource(), false, startUp);
                                synchronized (handler) {
                                    runOnUiThread(handler);
                                    try {
                                        handler.wait();
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        });
                        final int finalI = i;
                        rack.phidgets[i].phidget.addSensorChangeListener(new SensorChangeListener() {
                            public void sensorChanged(SensorChangeEvent se) {
                                runOnUiThread(new SensorChangeRunnable(finalI, se.getIndex(), se.getValue()));
                            }
                        });
                        rack.phidgets[i].phidget.open(rack.phidgets[i].phidgetSerialNumber, phidgetServerAddress, 5001);
                    }
                } catch (PhidgetException pe) {
                    pe.printStackTrace();
                }

                listView.setAdapter(aa);
                if (resume) listView.smoothScrollToPosition(testRun.currentBay);
                if (testRun.currentTestStep.equals(1)) {
                    // Make sure to put the cursor on the correct field
                    // Check the first bay
                    Integer targetBay = testRun.setNextBarcodeEditField();
                    listView.setSelection(targetBay);
                }
                updateCounts();
                updateView();
            } else {
                // Rack is not initialized, everything will fail
                // Probably not connected to the database on the network
                // Show a toast saying network problems
                CharSequence text = "Cannot start, database not found.";
                int duration = Toast.LENGTH_SHORT;
                Toast toast = Toast.makeText(context, text, duration);
                toast.show();
            }
        }
    }

    private void updatePhidgetSettings() {
        // Need to start the phidget reading callbacks now
        // add the phidget interface stuff for the real time value.
        Integer sensorChangeTrigger = resources.getInteger(R.integer.PHIDGET_SENSOR_CHANGE_TRIGGER);
        Integer dataRate = resources.getInteger(R.integer.PHIDGET_DATA_RATE);
        Boolean ratioMetric = resources.getBoolean(R.bool.PHIDGET_RATIO_METRIC);

        try {
            if (rack.phidgets[0].phidget.isAttached()) {
                for (int i = 0; i < 8; i++) {
                    rack.phidgets[0].phidget.setDataRate(i, dataRate);
                    rack.phidgets[0].phidget.setSensorChangeTrigger(i, sensorChangeTrigger);
                }
                rack.phidgets[0].phidget.setRatiometric(false);
            }

            if (rack.phidgets[1].phidget.isAttached()) {
                for (int i = 0; i < 8; i++) {
                    rack.phidgets[1].phidget.setDataRate(i, dataRate);
                    rack.phidgets[1].phidget.setSensorChangeTrigger(i, sensorChangeTrigger);
                }
                rack.phidgets[1].phidget.setRatiometric(false);
            }

            if (rack.phidgets[2].phidget.isAttached()) {
                for (int i = 0; i < 8; i++) {
                    rack.phidgets[2].phidget.setDataRate(i, dataRate);
                    rack.phidgets[2].phidget.setSensorChangeTrigger(i, sensorChangeTrigger);
                }
                rack.phidgets[2].phidget.setRatiometric(ratioMetric);
            }
        } catch (PhidgetException pe) {
            pe.printStackTrace();
        }
    }
}
