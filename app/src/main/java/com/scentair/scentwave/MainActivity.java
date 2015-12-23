package com.scentair.scentwave;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

//import com.crittercism.app.Crittercism;

public class MainActivity extends Activity {

    // Need some global variables to store array values
    public static Operators operators;
    public static TestSteps testSteps;
    public static Failures failures;
    private Boolean resumeRun = false;
    private Boolean dbLoadError = false;

    // Admin screen locking status variables
    private Boolean onePressed = false;
    private Boolean twoPressed = false;
    private Boolean threePressed = false;

    // These are the tags for the data stored in NVM as preferences
    public static final String TAG_MYPREFS = "ScentwavePrefs";
    public static final String TAG_OPERATOR_NAME = "OPERATOR_NAME";
    public static final String TAG_RESUME_AVAILABLE = "RESUME_AVAILABLE";
    public static final String TAG_RACK_NUMBER = "RACK_NUMBER";
    public static final String TAG_DATABASE_SERVER_ADDRESS="DATABASE_SERVER_ADDRESS";
    public static final String TAG_PHIDGET_SERVER_ADDRESS="PHIDGET_SERVER_ADDRESS";
    public static final String DEBUG_BARCODE_OVERRIDE="DEBUG_BARCODE_OVERRIDE";

    SharedPreferences sharedPreferences;
    public static String phidgetServerAddress;
    public static String dbServerAddress;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

      //  Crittercism.initialize(getApplicationContext(),"553f93548172e25e67906ac8");
        setContentView(R.layout.main);

        sharedPreferences=getSharedPreferences(TAG_MYPREFS, Context.MODE_PRIVATE);
        phidgetServerAddress = sharedPreferences.getString(TAG_PHIDGET_SERVER_ADDRESS,"192.168.1.22");
        dbServerAddress = sharedPreferences.getString(TAG_DATABASE_SERVER_ADDRESS,"192.168.1.26");
        resumeRun = sharedPreferences.getBoolean(TAG_RESUME_AVAILABLE,false);

        // Set up the admin triggers
        View adminOne = findViewById(R.id.touch_1);
        adminOne.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                adminTouch(1);
            }
        });
        adminOne = findViewById(R.id.touch_2);
        adminOne.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                adminTouch(2);
            }
        });
        adminOne = findViewById(R.id.touch_3);
        adminOne.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                adminTouch(3);
            }
        });
        adminOne = findViewById(R.id.touch_4);
        adminOne.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                adminTouch(4);
            }
        });

        //start initialization from the DB in an asynchronous task
        new loadDBValues().execute("http://this is a test");

        // Load up shared prefs and populate fields
        updateView();
    }

    // layout button triggers this method to start new activity
    public void startTestRun (View view) {
        Intent intent = new Intent(this,TestRunActivity.class);
        startActivity(intent);
    }

    // layout button triggers this method to start new activity
    // This button is hidden unless a resume is possible
    public void resumeTestRun (View view) {
        Intent intent = new Intent(this,TestRunActivity.class);
        intent.putExtra(TAG_RESUME_AVAILABLE, true);
        startActivity(intent);
    }

    // layout button triggers this method to start new activity
    public void startChangeOperator (View view) {
        Intent intent = new Intent(this,ChangeOperatorActivity.class);
        startActivity(intent);
    }

    private class loadDBValues extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {
            try {
                operators = new Operators(dbServerAddress);
                testSteps = new TestSteps(dbServerAddress);
                failures = new Failures(dbServerAddress);

            } catch (Exception e) {
                e.printStackTrace();
                dbLoadError=true;
            }

            return urls[0];
        }
        @Override
        protected void onPostExecute(String result) {
            if (dbLoadError) {
                Toast.makeText(getApplicationContext(), "Update DB address in preferences", Toast.LENGTH_LONG).show();
                TextView textView = (TextView) findViewById(R.id.db_load_error);
                textView.setVisibility(View.VISIBLE);
            } else {
                TextView textView = (TextView) findViewById(R.id.db_load_error);
                textView.setVisibility(View.INVISIBLE);
                Toast.makeText(getApplicationContext(), "Database settings loaded", Toast.LENGTH_LONG).show();
            }

        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (dbLoadError) {
            // try to load the DB values again
            new loadDBValues().execute("http://this is a test");
        }
        updateView();
    }

    private void updateView(){
        //Load up the shared preferences
        String currentOperator = sharedPreferences.getString(TAG_OPERATOR_NAME,null);
        if (currentOperator==null) {
            currentOperator="not set";
        }
        TextView textView = (TextView) findViewById(R.id.current_operator);
        textView.setText(currentOperator);

        resumeRun = sharedPreferences.getBoolean(TAG_RESUME_AVAILABLE,false);
        if (resumeRun) {
            // The tests were not completed, offer the option to start at the last step completed.
            Button button = (Button) findViewById(R.id.ResumeTestButton);
            button.setVisibility(Button.VISIBLE);
        }
        else {
            Button button = (Button) findViewById(R.id.ResumeTestButton);
            button.setVisibility(Button.INVISIBLE);
        }

        textView = (TextView) findViewById(R.id.db_load_error);

        if (dbLoadError) {
            textView.setVisibility(View.VISIBLE);
            Toast.makeText(getApplicationContext(), "Update DB address in preferences", Toast.LENGTH_LONG).show();
        } else textView.setVisibility(View.INVISIBLE);
    }

    private void adminTouch(Integer pressed) {
        // User has pressed one of the admin screen areas
        switch(pressed) {
            case 1:
                // Start over
                onePressed=true;
                twoPressed=false;
                threePressed=false;
                break;
            case 2:
                if (onePressed) {
                    twoPressed=true;
                } else {
                    onePressed=false;
                    twoPressed=false;
                }
                threePressed=false;
                break;
            case 3:
                if (onePressed && twoPressed) {
                    threePressed=true;
                } else {
                    onePressed=false;
                    twoPressed=false;
                    threePressed=false;
                }
                break;
            case 4:
                if (onePressed && twoPressed && threePressed) {
                    // Start the admin activity
                    Intent intent = new Intent(this, AdminActivity.class);
                    startActivity(intent);
                }
                // Reset all triggers and require correct taps to re-enter
                onePressed=false;
                twoPressed=false;
                threePressed=false;
                break;
            default:
                // reset all status variables;
                onePressed=false;
                twoPressed=false;
                threePressed=false;
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}