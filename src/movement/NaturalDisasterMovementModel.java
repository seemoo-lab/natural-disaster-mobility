/*
 * Copyright 2015 Tom Schons - TU Darmstadt, Germany
 * Released under GPLv3. See LICENSE.txt for details.
 */
package movement;

import core.Coord;
import core.Settings;
import core.SimClock;
import java.util.Random;
import core.SimClock;

/**
 *
 * This movement model simulates disaster relief efforts after a natural disaster,
 * such as, for example, typhoons, floodings, earthquakes, etc. 
 * 
 * The model covers relief efforts from day zero until the end of a defined period (specifiable
 * via the settings file) after the disaster occurred. 
 * This class is the main movement model class.
 * Further sub-models are listed below and put into separate classes for conveniance and modularity reasons.
 * 
 * @author Tom Schons
 */

public class NaturalDisasterMovementModel extends ExtendedMovementModel {

	// Constants for importing settings from default settings file
	public static final String GROUP_ID = "groupID";
	public static final String DAY_LENGTH = "dayLength";
	public static final String NUMBER_OF_DAYS = "nbrOfDays";

	// Sub-movement models we load for scenario creation
	private SleepActivityMovement goSleepMM;
	private ArrivalActivityMovement airportArrivalMM;
	private GoToAirportActivityMovement goToAirportMM;
	private OfficialsActivityMovement officialsMM;
	private NonInjuredPopulationActivityMovement nonInjuredMM;
	private InjuredPopulationActivityMovement injuredMM;
	private DisasterReliefActivityMovement disasterReliefMM;
	private ScientificActivityMovement scienceMM;
	private SearchAndRescueActivityMovement searchRescueMM;
	
	// Defining the differend modes for our simulation scenario
	// Individual is in sleep mode (during evening and night)
	private static final int SLEEP_MODE = 10;
	// Individual just arrived in the disaster area (in our case via the airport)
	private static final int ARRIVAL_MODE = 11;
	 // Individual is in disaster relief mode (used for simulating disaster relief organizations)
	private static final int DISASTER_RELIEF_MODE = 12;
	// Individual is in scientific mode (e.g. a scientist)
	private static final int SCIENTIFIC_MODE = 13; 
	// Individual is going to the airport & leaving the disaster area
	private static final int GO_AIRPORT_MODE = 14; 
	// Individual is in official mode (e.g. government or UN officials)
	private static final int OFFICIAL_MODE = 15; 
	 // Individual is in search and rescue mode (e.g. mostly urban search and rescue teams)
	private static final int SEARCH_RESCUE_MODE = 16;
	// Individual is in injured mode
	private static final int INJURED_MODE = 17; 
	 // Individual is in non-injured mode
	private static final int NON_INJURED_MODE = 18;
	
	// Defining the different groups for our simulation scenario
	// Scientists group ID
	private static final int SCIENTISTS_GROUP_ID = 100;
	// Injured group ID
	private static final int INJURED_GROUP_ID = 101;
	// Non-injured group ID
	private static final int NONINJURED_GROUP_ID = 102;
	// Search and rescue group ID
	private static final int SEARCH_RESCUE_GROUP_ID = 103;
	// Government group ID
	private static final int GOVERNMENT_GROUP_ID = 104;
	// UN group ID
	private static final int UN_GROUP_ID = 105;
	// Disaster relief organization group ID
	private static final int DRO_GROUP_ID = 106;
	
	// The mode in which our model is currently operating
	private int mode;
	// The group ID of the specific movement model 
	private int groupID;

	// Length of the day in seconds
	private double dayLength; 
	// Number of days
	private int nbrOfDays; 
	
	// Day Counter
	private int dayCounter; 
	
	/**
	 * Creates a new instance of NaturalDisasterMovementModel
	 * @param settings
	 */
	public NaturalDisasterMovementModel(Settings settings) { 
		super(settings);
		goSleepMM = new SleepActivityMovement(settings);
		airportArrivalMM = new ArrivalActivityMovement(settings);
		goToAirportMM = new GoToAirportActivityMovement(settings);
		scienceMM = new ScientificActivityMovement(settings);
		injuredMM = new InjuredPopulationActivityMovement(settings);
		nonInjuredMM = new NonInjuredPopulationActivityMovement(settings);
		searchRescueMM = new SearchAndRescueActivityMovement(settings);
		officialsMM = new OfficialsActivityMovement(settings);
		disasterReliefMM = new DisasterReliefActivityMovement(settings);
		
		this.dayCounter = 0;

		if (settings.contains(GROUP_ID)) {
			groupID = settings.getInt(GROUP_ID);
		}
		else {
			System.out.println("You didn't specify a value for the group ID!");
			System.out.println("groupID: " + this.groupID); 
		}
		if (settings.contains(DAY_LENGTH)) {
			dayLength = settings.getDouble(DAY_LENGTH);
		}
		else {
			System.out.println("You didn't specify a value for the day length!");
			System.out.println("dayLength: " + this.dayLength); 
		}
		if (settings.contains(NUMBER_OF_DAYS)) {
			nbrOfDays = settings.getInt(NUMBER_OF_DAYS);
		}
		else {
			System.out.println("You didn't specify a value for the number of days!");
			System.out.println("nbrOfDays: " + this.nbrOfDays); 
		}
		
		// Current movement model (e.g. activity) for the specific groups are set in accordance with their groupID
		switch (groupID) {
		case SCIENTISTS_GROUP_ID: {
			// Scientific experts often are already present in the area, thus their start their activity in sleep mode (within the disaster area) 
			setCurrentMovementModel(goSleepMM);
			mode = SLEEP_MODE;
			break;
		}
		case INJURED_GROUP_ID: {
			// Injured individuals obviously are already within the disaster area, thus they start their activity in sleep mode 
			setCurrentMovementModel(goSleepMM); 
			mode = SLEEP_MODE;
			break;
		}
		case NONINJURED_GROUP_ID: {
			// Non-injured individuals obviously are already within the disaster area, thus they start their activity in sleep mode 
			setCurrentMovementModel(goSleepMM); 
			mode = SLEEP_MODE;
			break;
		}
		case SEARCH_RESCUE_GROUP_ID: {
			// Search and rescue groups usually arrive via the airport after the disaster occurred, thus they start their activity in the airportArrivalMM movement model 
			setCurrentMovementModel(airportArrivalMM);
			mode = ARRIVAL_MODE;
			break;
		}
		case GOVERNMENT_GROUP_ID: {
			// Government officials are usually present within the disaster area and thus often start their activity in sleep mode 
			setCurrentMovementModel(goSleepMM);  
			mode = SLEEP_MODE;
			break;
		}
		case UN_GROUP_ID: {
			// UN officials are usually not present within the disaster area and thus start their activity in the airportArrivalMM movement model 
			setCurrentMovementModel(airportArrivalMM);  
			mode = ARRIVAL_MODE;
			break;
		}
		case DRO_GROUP_ID: {
			// Disaster relief organizations are usually not present within the disaster area and thus start their activity in the airportArrivalMM movement model
			setCurrentMovementModel(airportArrivalMM);
			mode = ARRIVAL_MODE;
			break;
		}
		default:
			System.out.println("Group ID not properly set - ERROR - Did you check the default settings file parameters for correctness?");
			break;
		}
	}

	/**
	 * Creates a new instance of NaturalDisasterMovementModel from a prototype
	 * @param prototype
	 */
	public NaturalDisasterMovementModel(NaturalDisasterMovementModel prototype) {
		// Import settings with all parameters from prototype
		super(prototype);
		goSleepMM = new SleepActivityMovement(prototype.goSleepMM);
		airportArrivalMM = new ArrivalActivityMovement(prototype.airportArrivalMM);
		goToAirportMM = new GoToAirportActivityMovement(prototype.goToAirportMM);
		scienceMM = new ScientificActivityMovement(prototype.scienceMM);
		injuredMM = new InjuredPopulationActivityMovement(prototype.injuredMM);
		nonInjuredMM = new NonInjuredPopulationActivityMovement(prototype.nonInjuredMM);
		searchRescueMM = new SearchAndRescueActivityMovement(prototype.searchRescueMM);
		officialsMM = new OfficialsActivityMovement(prototype.officialsMM);
		disasterReliefMM = new DisasterReliefActivityMovement(prototype.disasterReliefMM);
		
		this.dayCounter = 0;
		
		this.groupID = prototype.getGroupID();
		this.dayLength = prototype.getDayLength();
		this.nbrOfDays = prototype.getNbrOfDays();
		
		// Current movement model (e.g. activity) for the specific groups are set in accordance with their groupID
		switch (groupID) {
		case SCIENTISTS_GROUP_ID: {
			// Scientific experts often are already present in the area, thus their start their activity in sleep mode (within the disaster area) 
			setCurrentMovementModel(goSleepMM);
			mode = SLEEP_MODE;
			break;
		}
		case INJURED_GROUP_ID: {
			// Injured individuals obviously are already within the disaster area, thus they start their activity in sleep mode 
			setCurrentMovementModel(goSleepMM); 
			mode = SLEEP_MODE;
			break;
		}
		case NONINJURED_GROUP_ID: {
			// Non-injured individuals obviously are already within the disaster area, thus they start their activity in sleep mode 
			setCurrentMovementModel(goSleepMM); 
			mode = SLEEP_MODE;
			break;
		}
		case SEARCH_RESCUE_GROUP_ID: {
			// Search and rescue groups usually arrive via the airport after the disaster occurred, thus they start their activity in the airportArrivalMM movement model 
			setCurrentMovementModel(airportArrivalMM);
			mode = ARRIVAL_MODE;
			break;
		}
		case GOVERNMENT_GROUP_ID: {
			// Government officials are usually present within the disaster area and thus often start their activity in sleep mode 
			setCurrentMovementModel(goSleepMM);  
			mode = SLEEP_MODE;
			break;
		}
		case UN_GROUP_ID: {
			// UN officials are usually not present within the disaster area and thus start their activity in the airportArrivalMM movement model 
			setCurrentMovementModel(airportArrivalMM);  
			mode = ARRIVAL_MODE;
			break;
		}
		case DRO_GROUP_ID: {
			// Disaster relief organizations are usually not present within the disaster area and thus start their activity in the airportArrivalMM movement model
			setCurrentMovementModel(airportArrivalMM);
			mode = ARRIVAL_MODE;
			break;
		}
		default:
			System.out.println("Group ID not properly set - ERROR - Did you check the default settings file parameters for correctness?");
			break;
		}
	}

	/**
	 * Method is called between each getPath() request when the current MM is ready (isReady() method returns true).
	 * Subclasses implement all changes of state that need to be made.
	 * 
	 * This functions holds all basic structures for switching the different activities in a disater area
	 *  
	 * @return true if success
	 */
	@Override
	public boolean newOrders() {
		// Always calculating the number of the days passed by when calling newOrders()
		if ((SimClock.getTime() > ((this.dayCounter + 1) * this.dayLength)) && (!(this.dayCounter == this.nbrOfDays))) {
			// Simulation is not yet finished but another day passed by
			this.dayCounter++; 
			System.out.println("Another day passed by! - newOrders() - Value of this.dayCounter is now = " + this.dayCounter);
			// Update the sleep activity since counter is usually updated after we finished the sleep activity -> need to be updated retroactively 
			goSleepMM.updateDayCounter(this.dayCounter); 
		}
		
		// Now checking what mode we are operating in and then proceeding to do what is necessary according to that mode of operation		
		switch (mode) {
		// Switching between the different modes of activity
		case SLEEP_MODE: { 
			if (goSleepMM.isReady()) {
				// If true we are done with sleeping and want to become active again since the next day has just started
				if (this.dayCounter == this.nbrOfDays){
					// If we end up here the simulation is finished -> we idle forever  
					System.out.println("------------SIMULATION IS OVER------------");
					System.out.println("------------SIMULATION IS OVER------------");
					System.out.println("------------SIMULATION IS OVER------------");
					System.out.println("------------SIMULATION IS OVER------------");
					System.out.println("------------SIMULATION IS OVER------------");
					break;
				}
				if (groupID == SCIENTISTS_GROUP_ID) {
					// Switching to the next movement model
					if (!scienceMM.getGoToAirport()) {
						// we remain in the science movement model
						setCurrentMovementModel(scienceMM); 
						// set mode to accurate model 
						mode = SCIENTIFIC_MODE;
						// Activate the correct movement model again and update its dayCounter
						scienceMM.Activate(this.dayCounter);
					}
					else {
						// we leave the disaster area for going to the airport 
						setCurrentMovementModel(goToAirportMM); 
						// set mode to accurate model 
						mode = GO_AIRPORT_MODE;
						// Set the last location of the science activity to be the initial location of the go to airport activity
						goToAirportMM.setInitialLocation(scienceMM.getLastLocation().clone());
						// Activate the correct movement model again and update its dayCounter
						goToAirportMM.Activate();
					}
				}
				if (groupID == INJURED_GROUP_ID) {
					// Switching to the next movement model
					setCurrentMovementModel(injuredMM);
					// set mode to accurate model 
					mode = INJURED_MODE;
					// Activate the correct movement model again and update its dayCounter
					injuredMM.Activate(this.dayCounter); 
				}
				if (groupID == NONINJURED_GROUP_ID) {
					// Switching to the next movement model
					setCurrentMovementModel(nonInjuredMM); 
					// set mode to accurate model 
					mode = NON_INJURED_MODE;
					// Activate the correct movement model again and update its dayCounter
					nonInjuredMM.Activate(this.dayCounter);
				}
				if (groupID == SEARCH_RESCUE_GROUP_ID) {
					// Switching to the next movement model
					if (!searchRescueMM.getGoToAirport()) {
						// we remain in the search and rescue movement model
						setCurrentMovementModel(searchRescueMM); 
						// set mode to accurate model 
						mode = SEARCH_RESCUE_MODE;
						// Activate the correct movement model again and update its dayCounter
						searchRescueMM.Activate(this.dayCounter);
					}
					else {
						// we leave the search and rescue model for going to the airport 
						setCurrentMovementModel(goToAirportMM); 
						// set mode to accurate model 
						mode = GO_AIRPORT_MODE;
						// Set the last location of the search and rescue activity to be the initial location of the go to airport activity
						goToAirportMM.setInitialLocation(searchRescueMM.getLastLocation().clone());
						// Activate the correct movement model again and update its dayCounter
						goToAirportMM.Activate();
					}
				}
				if (groupID == GOVERNMENT_GROUP_ID) {
					// Switching to the next movement model
					setCurrentMovementModel(officialsMM); 
					// set mode to accurate model 
					mode = OFFICIAL_MODE;
					// Activate the correct movement model again and update its dayCounter
					officialsMM.Activate(this.dayCounter);
				}
				if (groupID == UN_GROUP_ID) {
					// Switching to the next movement model
					setCurrentMovementModel(officialsMM); 
					// set mode to accurate model 
					mode = OFFICIAL_MODE;
					// Activate the correct movement model again and update its dayCounter
					officialsMM.Activate(this.dayCounter);
				}
				if (groupID == DRO_GROUP_ID) {
					// Switching to the next movement model
					setCurrentMovementModel(disasterReliefMM); 
					// set mode to accurate model 
					mode = DISASTER_RELIEF_MODE;
					// Activate the correct movement model again and update its dayCounter
					disasterReliefMM.Activate(this.dayCounter);
				}
			}			
			break;
		}
		case ARRIVAL_MODE: { 
			if (airportArrivalMM.isReady()) {
				// If true we are done with the arrival at airport part and want to become active in the disaster area
				// Set activity according to group ID
				if (groupID == SCIENTISTS_GROUP_ID) {
					// throw error since SCIENTISTS should'nt be in arrival mode at any time
				}
				if (groupID == INJURED_GROUP_ID) {
					// throw error since injured should'nt be in arrival mode at any time
				}
				if (groupID == NONINJURED_GROUP_ID) {
					// throw error since non injured should'nt be in arrival mode at any time
				}
				if (groupID == SEARCH_RESCUE_GROUP_ID) {
					// Mandatory to set initial location in order to avoid null pointer exceptions since this activity starts after the arrival at airport activity
					searchRescueMM.setInitialLocation(airportArrivalMM.getLastLocation().clone()); 
					setCurrentMovementModel(searchRescueMM);
					mode = SEARCH_RESCUE_MODE;
					// Activate the correct movement model again and update its dayCounter
					searchRescueMM.Activate(this.dayCounter);
				}
				if (groupID == GOVERNMENT_GROUP_ID) {
					// throw error since injured should'nt be in arrival mode at any time
				}
				if (groupID == UN_GROUP_ID) {
					// Mandatory to set initial location in order to avoid that UN group is mispositioned on the map since activity starts at the city center when location is not explicitly set! (fallback mode)
					officialsMM.setInitialLocation(airportArrivalMM.getLastLocation().clone()); 
					setCurrentMovementModel(officialsMM);
					mode = OFFICIAL_MODE;
					// Activate the correct movement model again and update its dayCounter
					officialsMM.Activate(this.dayCounter);
				}
				if (groupID == DRO_GROUP_ID) {
					// Mandatory to set initial location in order to avoid null pointer exceptions since this activity starts after the arrival at airport activity
					disasterReliefMM.setInitialLocation(airportArrivalMM.getLastLocation().clone()); 
					setCurrentMovementModel(disasterReliefMM);
					mode = DISASTER_RELIEF_MODE;
					// Activate the correct movement model again and update its dayCounter
					disasterReliefMM.Activate(this.dayCounter);
				}
			}			
			break;
		}
		case DISASTER_RELIEF_MODE: { 
			if (disasterReliefMM.isReady()) {
				// If true we are done with the disaster relief activity for today and want to go home / to sleep (generally speaking)
				// Set activity according to group ID
				if (groupID == SCIENTISTS_GROUP_ID) {
					// throw error since we should'nt be in disaster relief mode at any time
				}
				if (groupID == INJURED_GROUP_ID) {
					// throw error since we should'nt be in disaster relief mode at any time
				}
				if (groupID == NONINJURED_GROUP_ID) {
					// throw error since we should'nt be in disaster relief mode at any time
				}
				if (groupID == SEARCH_RESCUE_GROUP_ID) {
					// throw error since we should'nt be in disaster relief mode at any time
				}
				if (groupID == GOVERNMENT_GROUP_ID) {
					// throw error since we should'nt be in disaster relief mode at any time
				}
				if (groupID == UN_GROUP_ID) {
					// throw error since we should'nt be in disaster relief mode at any time
				}
				if (groupID == DRO_GROUP_ID) {
					// Day is over, going home to sleep -> Activating sleep activity
					setCurrentMovementModel(goSleepMM);
					mode = SLEEP_MODE;
					goSleepMM.Activate(this.dayCounter); 
					System.out.println("Sleeping in diaster relief mode");
				}
			}			
			break;
		}
		case SCIENTIFIC_MODE: { 
			if (scienceMM.isReady()) {
				// If true we are done with the scientific part and want to go home / to sleep (generally speaking)
				// Set activity according to group ID
				if (groupID == SCIENTISTS_GROUP_ID) {
					// Day is over, going home to sleep -> Activating sleep activity
					setCurrentMovementModel(goSleepMM);
					mode = SLEEP_MODE;
					goSleepMM.Activate(this.dayCounter); 
					System.out.println("Sleeping in scientific mode");
				}
				if (groupID == INJURED_GROUP_ID) {
					// throw error since we should'nt be in scientific mode at any time
				}
				if (groupID == NONINJURED_GROUP_ID) {
					// throw error since we should'nt be in scientific mode at any time
				}
				if (groupID == SEARCH_RESCUE_GROUP_ID) {
					// throw error since we should'nt be in scientific mode at any time
				}
				if (groupID == GOVERNMENT_GROUP_ID) {
					// throw error since we should'nt be in scientific mode at any time
				}
				if (groupID == UN_GROUP_ID) {
					// throw error since we should'nt be in scientific mode at any time
				}
				if (groupID == DRO_GROUP_ID) {
					// throw error since we should'nt be in scientific mode at any time
				}
			}			
			break;
		}
		case GO_AIRPORT_MODE: {
			if (goToAirportMM.isReady()) {
				// If true we are done with disaster relief and already at the airport -> so we solely idle here 
				// Set activity according to group ID
				if (groupID == SCIENTISTS_GROUP_ID) {
					System.out.println("Simulation is over for this scientist node - Idle mode ON"); 
				}
				if (groupID == INJURED_GROUP_ID) {
					// Throw an error since we shouldn't use this mode
				}
				if (groupID == NONINJURED_GROUP_ID) {
					// Throw an error since we shouldn't use this mode
				}
				if (groupID == SEARCH_RESCUE_GROUP_ID) {
					System.out.println("Simulation is over for this search and rescue node - Idle mode ON"); 
				}
				if (groupID == GOVERNMENT_GROUP_ID) {
					System.out.println("Simulation is over for this officials node - Idle mode ON"); 
				}
				if (groupID == UN_GROUP_ID) {
					System.out.println("Simulation is over for this officials node - Idle mode ON"); 
				}
				if (groupID == DRO_GROUP_ID) {
					System.out.println("Simulation is over for this disaster relief organization member node - Idle mode ON"); 
				}
			}			
			break;
		}
		case OFFICIAL_MODE: { 
			if (officialsMM.isReady()) {
				// If true we are done with the officials activity and want to go home / to sleep (generally speaking)
				// Set activity according to group ID
				if (groupID == SCIENTISTS_GROUP_ID) {
					// throw error since we should'nt be in officials mode at any time
				}
				if (groupID == INJURED_GROUP_ID) {
					// throw error since we should'nt be in officials mode at any time
				}
				if (groupID == NONINJURED_GROUP_ID) {
					// throw error since we should'nt be in officials mode at any time
				}
				if (groupID == SEARCH_RESCUE_GROUP_ID) {
					// throw error since we should'nt be in officials mode at any time
				}
				if (groupID == GOVERNMENT_GROUP_ID) {
					// Day is over, going home to sleep -> Activating sleep activity
					setCurrentMovementModel(goSleepMM);
					mode = SLEEP_MODE;
					goSleepMM.Activate(this.dayCounter); 
					System.out.println("Sleeping in governement mode");
				}
				if (groupID == UN_GROUP_ID) {
					// Day is over, going home to sleep -> Activating sleep activity
					setCurrentMovementModel(goSleepMM);
					mode = SLEEP_MODE;
					goSleepMM.Activate(this.dayCounter); 
					System.out.println("Sleeping in UN group mode");
				}
				if (groupID == DRO_GROUP_ID) {
					// throw error since we should'nt be in officials mode at any time
				}
			}			
			break;
		}
		case SEARCH_RESCUE_MODE: { 
			if (searchRescueMM.isReady()) {
				// If true we are done with the search and rescue activity and want to go home / to sleep (generally speaking)
				// Set activity according to group ID
				if (groupID == SCIENTISTS_GROUP_ID) {
					// throw error since we should'nt be in searchRescue mode at any time
				}
				if (groupID == INJURED_GROUP_ID) {
					// throw error since we should'nt be in searchRescue mode at any time
				}
				if (groupID == NONINJURED_GROUP_ID) {
					// throw error since we should'nt be in searchRescue mode at any time
				}
				if (groupID == SEARCH_RESCUE_GROUP_ID) {
					// Day is over, going home to sleep -> Activating sleep activity
					setCurrentMovementModel(goSleepMM);
					mode = SLEEP_MODE;
					goSleepMM.Activate(this.dayCounter); 
					System.out.println("Sleeping in search and rescue mode");
				}
				if (groupID == GOVERNMENT_GROUP_ID) {
					// throw error since we should'nt be in searchRescue mode at any time
				}
				if (groupID == UN_GROUP_ID) {
					// throw error since we should'nt be in searchRescue mode at any time
				}
				if (groupID == DRO_GROUP_ID) {
					// throw error since we should'nt be in searchRescue mode at any time
				}
			}			
			break;
		}
		case INJURED_MODE: { 
			if (injuredMM.isReady()) {
				// If true we are done with our daily activity and want to go home / to sleep (generally speaking)
				// Set activity according to group ID
				if (groupID == SCIENTISTS_GROUP_ID) {
					// throw error since we should'nt be in injured mode at any time
				}
				if (groupID == INJURED_GROUP_ID) {
					// Day is over, going home to sleep -> Activating sleep activity
					setCurrentMovementModel(goSleepMM);
					mode = SLEEP_MODE;
					System.out.println("Switching back to sleep activity - Starting sleep in injured mode");
					goSleepMM.Activate(this.dayCounter); 
				}
				if (groupID == NONINJURED_GROUP_ID) {
					// throw error since we should'nt be in injured mode at any time
				}
				if (groupID == SEARCH_RESCUE_GROUP_ID) {
					// throw error since we should'nt be in injured mode at any time
				}
				if (groupID == GOVERNMENT_GROUP_ID) {
					// throw error since we should'nt be in injured mode at any time
				}
				if (groupID == UN_GROUP_ID) {
					// throw error since we should'nt be in injured mode at any time
				}
				if (groupID == DRO_GROUP_ID) {
					// throw error since we should'nt be in injured mode at any time
				}
			}			
			break;
		}
		case NON_INJURED_MODE: {
			if (nonInjuredMM.isReady()) {
				// If true we are done with our daily activity and want to go home / to sleep (generally speaking)
				// Set activity according to group ID
				if (groupID == SCIENTISTS_GROUP_ID) {
					// throw error since we should'nt be in non-injured mode at any time
				}
				if (groupID == INJURED_GROUP_ID) {
					// throw error since we should'nt be in non-injured mode at any time
				}
				if (groupID == NONINJURED_GROUP_ID) {
					// Day is over, going home to sleep -> Activating sleep activity
					setCurrentMovementModel(goSleepMM);
					mode = SLEEP_MODE;
					goSleepMM.Activate(this.dayCounter); 
					System.out.println("Sleeping in non injured mode");
				}
				if (groupID == SEARCH_RESCUE_GROUP_ID) {
					// throw error since we should'nt be in non-injured mode at any time
				}
				if (groupID == GOVERNMENT_GROUP_ID) {
					// throw error since we should'nt be in non-injured mode at any time
				}
				if (groupID == UN_GROUP_ID) {
					// throw error since we should'nt be in non-injured mode at any time
				}
				if (groupID == DRO_GROUP_ID) {
					// throw error since we should'nt be in non-injured mode at any time
				}
			}			
			break;
		}
		default:
			System.out.println("Group ID not properly set - ERROR - Did you check the default settings file parameters for correctness?");
			break;
		}
		return true;
	}

	@Override
	public Coord getInitialLocation() {
		// Returning the last location of each sub-movement model as the initial location since each sub-movement model is configured with it's last location being the initial location
		if (groupID == SEARCH_RESCUE_GROUP_ID) {
			return searchRescueMM.getLastLocation(); 
		}
		if (groupID == UN_GROUP_ID) {
			// Since we share the officialsMM between UN and government officials we need different functions to get the correct initial location
			return officialsMM.getSomeUNLocation(); 
		}
		if (groupID == DRO_GROUP_ID) {
			return disasterReliefMM.getLastLocation(); 
		}
		if (groupID == SCIENTISTS_GROUP_ID) {
			return scienceMM.getLastLocation(); 
		}
		if (groupID == INJURED_GROUP_ID) {
			return injuredMM.getLastLocation(); 
		}
		if (groupID == NONINJURED_GROUP_ID) {
			return nonInjuredMM.getLastLocation(); 
		}
		if (groupID == GOVERNMENT_GROUP_ID) {	
			// Since we share the officialsMM between UN and government officials we need different functions to get the correct initial location
			return officialsMM.getSomeGOVLocation(); 
		}
		System.out.println("Something went wrong when loading the initial location");
		return null;
	}

	@Override
	public MovementModel replicate() {
		return new NaturalDisasterMovementModel(this);
	}

	public int getGroupID() {
		return this.groupID;
	}

	public double getDayLength() {
		return this.dayLength; 
	}
	
	public int getNbrOfDays() {
		return this.nbrOfDays; 
	}
}