/*
 * Copyright 2015 Tom Schons - TU Darmstadt, Germany
 * Released under GPLv3. See LICENSE.txt for details.
 */
package movement;

import core.*;
import movement.naturaldisaster.*;

/**
 *
 * This movement model simulates disaster relief efforts after a natural disaster,
 * such as, for example, typhoons, floodings, earthquakes, etc. 
 *
 * The model covers relief efforts from day zero until the end of a defined period (specifiable
 * via the settings file) after the disaster occurred. 
 * This class is the main movement model class.
 * Further sub-models are listed below and put into separate classes for convenience and modularity reasons.
 *
 * @author Tom Schons
 */

public class NaturalDisasterMovementModel extends ExtendedMovementModel {

	/*
	 * Constants for importing settings from default settings file
	 */
	/** Role of an individual */
	public static final String ROLE = "role";
	/** Number of days for the simulation */
	public static final String NUMBER_OF_DAYS = "nbrOfDays";

	/** Length of the day in seconds */
	private static final int SECONDS_IN_A_DAY = 24 * 60 * 60;

	/*
	 * Sub-movement models we load for scenario creation
	 */
	private SleepActivityMovement goSleepMM;
	private ArrivalActivityMovement airportArrivalMM;
	private GoToAirportActivityMovement goToAirportMM;
	private OfficialsActivityMovement officialsMM;
	private NonInjuredPopulationActivityMovement nonInjuredMM;
	private InjuredPopulationActivityMovement injuredMM;
	private DisasterReliefActivityMovement disasterReliefMM;
	private ScientificActivityMovement scienceMM;
	private SearchAndRescueActivityMovement searchRescueMM;
	
	/*
	 * Different modes in which an individual can be in (depends on role)
	 */
	/** Individual is in sleep mode (during evening and night) */
	private static final int SLEEP_MODE = 10;
	/** Individual just arrived in the disaster area (in our case via the airport) */
	private static final int ARRIVAL_MODE = 11;
	/** Individual is in disaster relief mode (used for simulating disaster relief organizations) */
	private static final int DISASTER_RELIEF_MODE = 12;
	/** Individual is in scientific mode (e.g. a scientist) */
	private static final int SCIENTIFIC_MODE = 13;
	/** Individual is going to the airport & leaving the disaster area */
	private static final int GO_AIRPORT_MODE = 14;
	/** Individual is in official mode (e.g. government or UN officials) */
	private static final int OFFICIAL_MODE = 15;
	/** Individual is in search and rescue mode (e.g. mostly urban search and rescue teams) */
	private static final int SEARCH_RESCUE_MODE = 16;
	/** Individual is in injured mode */
	private static final int INJURED_MODE = 17;
	/** Individual is in non-injured mode */
	private static final int HEALTHY_MODE = 18;
	
	/*
	 * Different roles an individual can have
	 */
	/** Scientist role */
	private static final String SCIENTIST_ROLE = "Scientist";
	/** Injured local population role */
	private static final String INJURED_ROLE = "Injured";
	/** Healthy local population role */
	private static final String HEALTHY_ROLE = "Healthy";
	/** Search-and-Rescue team role */
	private static final String SEARCH_RESCUE_ROLE = "SnR";
	/** Government official role */
	private static final String GOVERNMENT_ROLE = "Government";
	/** UN official role */
	private static final String UN_ROLE = "UN";
	/** Disaster relief organization role */
	private static final String DRO_ROLE = "DRO";

	/** Current mode in which the individual currently is in */
	private int mode;

	/** The role of the individual that this model is run on */
	private final String role;

	/** Number of days */
	private int nbrOfDays;

	/** The current day */
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

		if (settings.contains(ROLE)) {
			role = settings.getSetting(ROLE);
		} else {
			throw new SettingsError("No Role specified (you need to specify Group.role when using NaturalDisasterMovementModel)");
		}
		if (settings.contains(NUMBER_OF_DAYS)) {
			nbrOfDays = settings.getInt(NUMBER_OF_DAYS);
		} else {
			throw new SettingsError("No value for the number of days specified");
		}

		setInitialActivity();
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

		this.role = new String(prototype.getRole());
		this.nbrOfDays = prototype.getNbrOfDays();

		setInitialActivity();
	}

	private void setInitialActivity() {
		// Current movement model (e.g. activity) for the specific groups are set in accordance with their role
		switch (role) {
			case SCIENTIST_ROLE: {
				// Scientific experts often are already present in the area, thus their start their activity in sleep mode (within the disaster area)
				setCurrentMovementModel(goSleepMM);
				mode = SLEEP_MODE;
				break;
			}
			case INJURED_ROLE: {
				// Injured individuals obviously are already within the disaster area, thus they start their activity in sleep mode
				setCurrentMovementModel(goSleepMM);
				mode = SLEEP_MODE;
				break;
			}
			case HEALTHY_ROLE: {
				// Non-injured individuals obviously are already within the disaster area, thus they start their activity in sleep mode
				setCurrentMovementModel(goSleepMM);
				mode = SLEEP_MODE;
				break;
			}
			case SEARCH_RESCUE_ROLE: {
				// Search and rescue groups usually arrive via the airport after the disaster occurred, thus they start their activity in the airportArrivalMM movement model
				setCurrentMovementModel(airportArrivalMM);
				mode = ARRIVAL_MODE;
				break;
			}
			case GOVERNMENT_ROLE: {
				// Government officials are usually present within the disaster area and thus often start their activity in sleep mode
				setCurrentMovementModel(goSleepMM);
				mode = SLEEP_MODE;
				break;
			}
			case UN_ROLE: {
				// UN officials are usually not present within the disaster area and thus start their activity in the airportArrivalMM movement model
				setCurrentMovementModel(airportArrivalMM);
				mode = ARRIVAL_MODE;
				break;
			}
			case DRO_ROLE: {
				// Disaster relief organizations are usually not present within the disaster area and thus start their activity in the airportArrivalMM movement model
				setCurrentMovementModel(airportArrivalMM);
				mode = ARRIVAL_MODE;
				break;
			}
			default:
				throw new SettingsError("Invalid role set (was '" + role + "')");
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
		if ((SimClock.getTime() > ((this.dayCounter + 1) * SECONDS_IN_A_DAY)) && (!(this.dayCounter == this.nbrOfDays))) {
			// Simulation is not yet finished but another day passed by
			this.dayCounter++;
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
						break;
					}
					if (role.equals(SCIENTIST_ROLE)) {
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
					if (role.equals(INJURED_ROLE)) {
						// Switching to the next movement model
						setCurrentMovementModel(injuredMM);
						// set mode to accurate model
						mode = INJURED_MODE;
						// Activate the correct movement model again and update its dayCounter
						injuredMM.Activate(this.dayCounter);
					}
					if (role.equals(HEALTHY_ROLE)) {
						// Switching to the next movement model
						setCurrentMovementModel(nonInjuredMM);
						// set mode to accurate model
						mode = HEALTHY_MODE;
						// Activate the correct movement model again and update its dayCounter
						nonInjuredMM.Activate(this.dayCounter);
					}
					if (role.equals(SEARCH_RESCUE_ROLE)) {
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
					if (role.equals(GOVERNMENT_ROLE)) {
						// Switching to the next movement model
						setCurrentMovementModel(officialsMM);
						// set mode to accurate model
						mode = OFFICIAL_MODE;
						// Activate the correct movement model again and update its dayCounter
						officialsMM.Activate(this.dayCounter);
					}
					if (role.equals(UN_ROLE)) {
						// Switching to the next movement model
						setCurrentMovementModel(officialsMM);
						// set mode to accurate model
						mode = OFFICIAL_MODE;
						// Activate the correct movement model again and update its dayCounter
						officialsMM.Activate(this.dayCounter);
					}
					if (role.equals(DRO_ROLE)) {
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
					if (role.equals(SEARCH_RESCUE_ROLE)) {
						// Mandatory to set initial location in order to avoid null pointer exceptions since this activity starts after the arrival at airport activity
						searchRescueMM.setInitialLocation(airportArrivalMM.getLastLocation().clone());
						setCurrentMovementModel(searchRescueMM);
						mode = SEARCH_RESCUE_MODE;
						// Activate the correct movement model again and update its dayCounter
						searchRescueMM.Activate(this.dayCounter);
					} else if (role.equals(UN_ROLE)) {
						// Mandatory to set initial location in order to avoid that UN group is mispositioned on the map since activity starts at the city center when location is not explicitly set! (fallback mode)
						officialsMM.setInitialLocation(airportArrivalMM.getLastLocation().clone());
						setCurrentMovementModel(officialsMM);
						mode = OFFICIAL_MODE;
						// Activate the correct movement model again and update its dayCounter
						officialsMM.Activate(this.dayCounter);
					} else if (role.equals(DRO_ROLE)) {
						// Mandatory to set initial location in order to avoid null pointer exceptions since this activity starts after the arrival at airport activity
						disasterReliefMM.setInitialLocation(airportArrivalMM.getLastLocation().clone());
						setCurrentMovementModel(disasterReliefMM);
						mode = DISASTER_RELIEF_MODE;
						// Activate the correct movement model again and update its dayCounter
						disasterReliefMM.Activate(this.dayCounter);
					} else {
						throw new SimError("Should not be in this mode");
					}
				}
				break;
			}
			case DISASTER_RELIEF_MODE: {
				if (disasterReliefMM.isReady()) {
					// If true we are done with the disaster relief activity for today and want to go home / to sleep (generally speaking)
					if (role.equals(DRO_ROLE)) {
						// Day is over, going home to sleep -> Activating sleep activity
						setCurrentMovementModel(goSleepMM);
						mode = SLEEP_MODE;
						goSleepMM.Activate(this.dayCounter);
					} else {
						throw new SimError("Should not be in this mode");
					}
				}
				break;
			}
			case SCIENTIFIC_MODE: {
				if (scienceMM.isReady()) {
					// If true we are done with the scientific part and want to go home / to sleep (generally speaking)
					// Set activity according to group ID
					if (role.equals(SCIENTIST_ROLE)) {
						// Day is over, going home to sleep -> Activating sleep activity
						setCurrentMovementModel(goSleepMM);
						mode = SLEEP_MODE;
						goSleepMM.Activate(this.dayCounter);
					} else {
						throw new SimError("Should not be in this mode");
					}
				}
				break;
			}
			case GO_AIRPORT_MODE: {
				if (goToAirportMM.isReady()) {
					// If true we are done with disaster relief and already at the airport -> so we solely idle here
				}
				break;
			}
			case OFFICIAL_MODE: {
				if (officialsMM.isReady()) {
					// If true we are done with the officials activity and want to go home / to sleep (generally speaking)
					if (role.equals(GOVERNMENT_ROLE)) {
						// Day is over, going home to sleep -> Activating sleep activity
						setCurrentMovementModel(goSleepMM);
						mode = SLEEP_MODE;
						goSleepMM.Activate(this.dayCounter);
					} else if (role.equals(UN_ROLE)) {
						// Day is over, going home to sleep -> Activating sleep activity
						setCurrentMovementModel(goSleepMM);
						mode = SLEEP_MODE;
						goSleepMM.Activate(this.dayCounter);
					} else {
						throw new SimError("Should not be in this mode");
					}
				}
				break;
			}
			case SEARCH_RESCUE_MODE: {
				if (searchRescueMM.isReady()) {
					// If true we are done with the search and rescue activity and want to go home / to sleep (generally speaking)
					if (role.equals(SEARCH_RESCUE_ROLE)) {
						// Day is over, going home to sleep -> Activating sleep activity
						setCurrentMovementModel(goSleepMM);
						mode = SLEEP_MODE;
						goSleepMM.Activate(this.dayCounter);
					} else {
						throw new SimError("Should not be in this mode");
					}
				}
				break;
			}
			case INJURED_MODE: {
				if (injuredMM.isReady()) {
					// If true we are done with our daily activity and want to go home / to sleep (generally speaking)
					if (role.equals(INJURED_ROLE)) {
						// Day is over, going home to sleep -> Activating sleep activity
						setCurrentMovementModel(goSleepMM);
						mode = SLEEP_MODE;
						goSleepMM.Activate(this.dayCounter);
					} else {
						throw new SimError("Should not be in this mode");
					}
				}
				break;
			}
			case HEALTHY_MODE: {
				if (nonInjuredMM.isReady()) {
					// If true we are done with our daily activity and want to go home / to sleep (generally speaking)
					if (role.equals(HEALTHY_ROLE)) {
						// Day is over, going home to sleep -> Activating sleep activity
						setCurrentMovementModel(goSleepMM);
						mode = SLEEP_MODE;
						goSleepMM.Activate(this.dayCounter);
					} else {
						throw new SimError("Should not be in this mode");
					}
				}
				break;
			}
			default:
				throw new SimError("Was in invalid mode " + mode);
		}
		return true;
	}

	@Override
	public Coord getInitialLocation() {
		// Returning the last location of each sub-movement model as the initial location since each sub-movement model is configured with it's last location being the initial location
		switch (role) {
			case SEARCH_RESCUE_ROLE: {
				return searchRescueMM.getLastLocation();
			}
			case UN_ROLE: {
				// Since we share the officialsMM between UN and government officials we need different functions to get the correct initial location
				return officialsMM.getSomeUNLocation();
			}
			case GOVERNMENT_ROLE: {
				// Since we share the officialsMM between UN and government officials we need different functions to get the correct initial location
				return officialsMM.getSomeGOVLocation();
			}
			case DRO_ROLE: {
				return disasterReliefMM.getLastLocation();
			}
			case SCIENTIST_ROLE: {
				return scienceMM.getLastLocation();
			}
			case INJURED_ROLE: {
				return injuredMM.getLastLocation();
			}
			case HEALTHY_ROLE: {
				return nonInjuredMM.getLastLocation();
			}
			default: {
				throw new SettingsError("Could not get initial location for role " + role);
			}
		}
	}

	@Override
	public MovementModel replicate() {
		return new NaturalDisasterMovementModel(this);
	}

	private String getRole() {
		return this.role;
	}

	public int getNbrOfDays() {
		return this.nbrOfDays;
	}
}