package com.scentair.scentwave;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.content.Context;
import android.widget.Toast;

public class BayItem{
    static final long ONE_MINUTE_IN_MILLIS=60000;
    public Integer bayNumber;
    public String mitecBarcode;
    public String scentairBarcode;
    public String unitState;
    // just setting this to something unusual for default.  -50 should never happen
    public Integer currentValue=-50;
    public String stepStatus;
    public Boolean isFailed;
    public String failCause;
    public Integer failCauseIndex;
    public Integer failStep;
    public Boolean isEditMitec;
    public Boolean isEditScentair;
    public Boolean isActive;
    public Integer lowValue = 0;
    private Date lowValueTimestamp;
    public Integer medValue = 0;
    private Date medValueTimestamp;
    public Integer highValue = 0;
    private Date highValueTimestamp;
    public String fanMedDisplayValue;
    public Boolean cycleTestComplete=false;
    public Date lastOffTime;
    private String oldUnitState;
    public String ledState;
    private Integer oldCurrentValue;

    //Constructor used for beginning a test run
    public BayItem (Integer bayNumber, boolean activeStatus) {
        this.bayNumber=bayNumber;
        this.mitecBarcode = "";
        this.scentairBarcode = "";
        this.currentValue = 0;
        this.oldCurrentValue= -50;
        this.stepStatus = "Not Tested";
        this.unitState = "Unplugged";
        this.oldUnitState = "Unplugged";
        this.failCause = "";
        this.failStep = 0;
        this.failCauseIndex=0;
        this.isEditMitec=false;
        this.isEditScentair=false;
        this.isActive = activeStatus;
        this.isFailed=false;
        this.lowValue=0;
        // This effectively nullifies the medium value check for the fans.  The logic will skip over this piece now.
        this.medValue=1;
        this.highValue=0;
        this.lastOffTime = new Date();
        this.cycleTestComplete=false;
        this.fanMedDisplayValue="";
        this.ledState="ON";
    }

    public String isPassReady(Integer testNumber) {
        // This is the logic that figures out whether a bay is ready to activate the Pass button
        // and move on to the next step
        // If the return value is not null, the string indicates the pending status before pass is enabled.
        String returnValue = "Error";
        if (isActive && !isFailed) {
            switch (testNumber) {
                case 1:
                    // Pass criteria for step 1 is both barcodes are entered.
                    // Data validation happens upon data entry, so we just need to check that both
                    // fields have a value here.
                    if (!this.mitecBarcode.isEmpty() && !this.scentairBarcode.isEmpty()) {
                        returnValue = "Passed";      // blue state
                        this.stepStatus = "Passed";  // green state
                    } else {
                        returnValue = "<font color=#2F4F4F>Barcodes not entered<br>Tap to clear last bay barcodes</br></font>";
                    }
                    break;
                case 2:
                    // Pass criteria for step 2 is the machine has been plugged in and the state is
                    // not 'Unplugged' and not recalibrate
                    if ((this.unitState.equals("Unplugged")) || (this.unitState.equals("Recalibrate"))) 
					{
                        returnValue = "<font color=#2F4F4F>Machine not plugged in</font>";
                    } 
					else returnValue = "Pass";
                    break;
                case 3:
                    // Pass criteria for step 3 is that values are recorded for each target fan speed
                    // fan values are saved when the unit state is settled on the target fan speed
                    if (lowValue != 0 && highValue != 0) 
					{
                       returnValue = "Pass";   // apparently this prompts something else to go all blue for the cell
                    }
					else 
					{  // Do we need to set one or neither of HIGH and or low as gray (or blue)
					   if (lowValue.equals(0) )
					   {
                          if (highValue.equals(0))   
						  {  // They should both be gray
                             returnValue = "<font color=#2F4F4F>Low Pending</font>\n<font color=#2F4F4F>High Pending</font>";
						  }
						  else
						  {  // Just Low is gray, High should be Blue
                             returnValue = "<font color=#2F4F4F>Low Pending</font>\n<font color=#0000FF>High Complete</font>";
						  }							  
                       } 
					   else  // the only option remaining
					   {  // High should be gray, Low is Blue
                          returnValue = "<font color=#0000FF>Low Complete</font>\n<font color=#2F4F4F>High Pending</font>";
                       }				
                    }
                    break;
                case 4:
                    // There is no automatic pass criteria, this step is all operator driven.
                    // So always return true.
                    returnValue = "Pass";
                    break;
                case 5:
                    // This is the final automated step.  Pass criteria is that a machine has cycled off->on->off
                    // within 2 minutes +/- 1
                    // Cycle test complete is set to true upon shift back to Off at the appointed time.
                    if (!cycleTestComplete) {
                        long curTimeinMs = lastOffTime.getTime();
                        Date nextOffTime = new Date(curTimeinMs + (2 * ONE_MINUTE_IN_MILLIS));
                        SimpleDateFormat format = new SimpleDateFormat("hh:mm:ss", Locale.US);
                        returnValue = format.format(nextOffTime);
                    } else {
                        returnValue = "Passed";
                        this.stepStatus = "Passed";
                    }
                    break;
            }
        }
        return returnValue;
    }

    public Boolean updateValue(Integer newValue, Integer testStepNumber, Context context) 
	{               
        Boolean refreshScreen = false;
        if (isActive && !isFailed) 
		{
            // Only process this if the bay is active in calibration and have not already failed
			oldCurrentValue = currentValue;
            currentValue = newValue; 
            // Check to see if our new value has triggered a state change.
			oldUnitState = unitState;
            unitState = TestRunActivity.machineStates.getState(newValue);
            // If we are in test step number 3, then start looking for the proper fan values
            // We only execute the logic to trap values if any of the necessary values are empty
			
			if ( testStepNumber.equals(3) )   //trying it without the timestamp thing at all
			{
			  if (oldCurrentValue.equals(newValue) )
		      {   //we did not have a state change (since we had an identical value received )
			    if (fanMedDisplayValue.equals("") )
			    {   // we haven't fully passed this bay yet
			       switch (oldUnitState)
				   {  // Update the stored fan current values based on the state. - it has already passed the consecutive test
                      case "Low":
					    if ( lowValue.equals(0) )
						{					     
                           lowValue = oldCurrentValue;
                           // lowValueTimestamp = null;
                           refreshScreen = true;
					    }				   
						break;					  
					  case "Medium":
					    if ( medValue.equals(0) )
						{					     
                           medValue = oldCurrentValue;
                           // medValueTimestamp = null;
                           refreshScreen = true;
					    }				   
						break;					  
					  case "High":
					  if ( highValue.equals(0) )
						{					     
                           highValue = oldCurrentValue;
                           // highValueTimestamp = null;
                           refreshScreen = true;
						}
					    break;
					  default:
			            break;
				   }
			    }
			  }
			}   // end of test step 3
			
		/*	
            if ( testStepNumber.equals(3) && 
			        ( fanMedDisplayValue.equals("") || lowValue.equals(0) || highValue.equals(0) ) )
            {
                if (!oldUnitState.equals(unitState)) {
                    // We have made a large shift.  Reset all timers so we wash out any old pass thru values
                    lowValueTimestamp=null;
                    medValueTimestamp=null;
                    highValueTimestamp=null;
                }
                switch (oldUnitState) {
                    // Update the stored trigger values based on the old state.
                    case "Unplugged":
                        break;
                    case "Low":
                        if (lowValue.equals(0)) {
                            if (lowValueTimestamp == null) {
                                // Get the first timestamp for Low
                                lowValueTimestamp = new Date();
                            } else {
                                if (oldCurrentValue.equals(newValue)) {
                                    // If this low value has been active for 1 second
                                    // save the last value stored
                                    lowValue = oldCurrentValue;
                                    lowValueTimestamp = null;
                                    refreshScreen = true;
                                } else {
                                    // Reset the timestamp
                                    lowValueTimestamp = new Date();
                                }
                            }
                        }
                        break;
                    case "Medium":
                        if ((medValue.equals(0))) {
                            if (medValueTimestamp == null) {
                                // Get the first timestamp for Medium
                                medValueTimestamp = new Date();
                            } else {
                                if (oldCurrentValue.equals(newValue)) {
                                    // If this low value has been active for 1 second or more
                                    // save the last value stored
                                    medValue = oldCurrentValue;
                                    medValueTimestamp = null;
                                    refreshScreen = true;
                                } else {
                                    // Reset the timestamp
                                    medValueTimestamp = new Date();
                                }
                            }
                        }
                        break;
                    case "High":
                        if (highValue.equals(0)) {
                            if (highValueTimestamp == null) {
                                // Get the first timestamp for High
                                highValueTimestamp = new Date();
                            } else {
                                if (oldCurrentValue.equals(newValue)) {
                                    // If this low value has been active for 1 second
                                    // save the last value stored
                                    highValue = oldCurrentValue;
                                    highValueTimestamp = null;
                                    refreshScreen = true;
                                } else {
                                    // Reset the timestamp
                                    highValueTimestamp = new Date();
                                }
                            }
                        }
                        break;
                    default:
                        break;
                }
            }
		*/				
           
            if (!cycleTestComplete) 
			{  // if the cycle timer has not already passed                 
                 // not passed so continue test
                 if (!highValue.equals(0) && !lowValue.equals(0) && !fanMedDisplayValue.equals(""))
			     {  // We only want the cycle timer to engage if we have already passed step 3
                   // Step 3 is only passed if we have values for high and low fan speeds and the display fan speed
                   if ( 
				       (oldUnitState.equals("Low") ||
                        oldUnitState.equals("Medium") ||
                        oldUnitState.equals("High") ||
                        oldUnitState.equals("Medium to High") ||
                        oldUnitState.equals("Low to Medium") ||
                        oldUnitState.equals("Fan OverLoad")      )
                    && (unitState.equals("BackLight Off") ||
                        unitState.equals("BackLight On") ||
                        unitState.equals("Unplugged") ||
                        unitState.equals("FanTurnOn") ||
                        unitState.equals("Recalibrate")  )   
					   )  
				   { // We have toggled from On to Off (in some form)
                     // Save the timestamp info for future reference
                     refreshScreen = true;
                     if (lastOffTime != null) // initial set in else below
				     {  // Check the time difference in milliseconds here                           
                        Date checkTime = new Date();                          
                        long difference = checkTime.getTime() - lastOffTime.getTime();
                        Integer seconds = (int) (difference / 1000);
                        // in any case, reset the date here so as not to smudge it with breakpoints... JTC changed to checktime
                        //lastOffTime = new Date();
						lastOffTime = checkTime;
                        switch (seconds) 
						{
                           case 117:
                           case 118:
                           case 119:
                           case 120:
                           case 121:
                           case 122:
                           case 123:
                             // We have a winner for fan cycle test timing.
                             cycleTestComplete = true;  
                             if ( testStepNumber.equals(5) ) 
			                 {
                               stepStatus = "Passed";
                               ledState = "OFF";
				               refreshScreen = true;
                             }								 
                             break;
                           default:
                             // No winner
							 // cycleTestComplete = false;
							 break;
						}
                        // Display some on screen feedback with some details on every end cycle counted
                        CharSequence text = "Bay:" + this.bayNumber + " reports " + seconds.toString() + " seconds";
                        int duration = Toast.LENGTH_SHORT;
                        Toast toast = Toast.makeText(context, text, duration);
                        toast.show();                            
                     } 
				     else 
				     {  // Set the reference time
                        lastOffTime = new Date();
                     }                        
                   }    // end end cycle detect
                 }   // end cycle 3 passed detect			
	        }  // end cycle test not complete check
        }     // end valid bay test
		return refreshScreen;        
    }

    public Integer getTransform (Integer position) 
	{
        Integer returnValue;
        // This is the new mapping protocol
		if (position<12) 
			{
                // This is the top/odd row  pos*2+1  [ so 1,3,5,... ]
                returnValue = (position*2) +1;
            } 
			else 			
                // This is the even/bottom row    pos*2 - 22   [ so 2,4,6,8... ]
                returnValue = (position*2) - 22 ;        
        return returnValue;
	}
     /*   if (position==0) 
		{
            returnValue=1;
        } 
		else 
		{
            if (position<12) 
			{
                // This is the top/odd row  pos*2+1  [ so 1,3,5,... ]
                returnValue = (position+1) + (position+1) - 1;
            } 
			else 
			{
                // This is the even/bottom row    pos*2 - 22   [ so 2,4,6,8... ]
                returnValue = 24 - (24-position) - (24-position) + 2;
            }
        }
        return returnValue;
       }
     */
    

    @Override
    public String toString() {
        return "BayItem [bayNumber=" + bayNumber +
                "mitecBarcode=" + mitecBarcode +
                "scentairbarcode=" + scentairBarcode +
                "currentValue=" + currentValue +
                "stepStatus=" + stepStatus +
                "failCause=" + failCause +
                "failStep=" + failStep +
                "failCauseIndex=" + failCauseIndex +
                "isEditMitec=" + isEditMitec +
                "isEditScentair=" + isEditScentair +
                "isActive=" + isActive +
                "isFailed=" + isFailed +
                "lowValue=" + lowValue +
                "medValue=" + medValue +
                "highValue=" + highValue +
                "cycleTestComplete" + cycleTestComplete +
                "]";
    }
}
