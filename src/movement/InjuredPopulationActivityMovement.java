/*
 * Copyright 2015 Tom Schons - TU Darmstadt, Germany
 * Released under GPLv3. See LICENSE.txt for details.
 */
package movement;

import java.util.List;

import movement.map.DijkstraPathFinder;
import movement.map.MapNode;
import core.Coord;
import core.Settings;

import input.WKTReader;
import java.io.File;
import java.util.LinkedList;
import java.util.List;
import movement.map.SimMap;
import java.util.Random;
import core.SimClock;

/**
 * The InjuredPopulationActivityMovement class represents the movement of injured people in a disaster zone
 *
 * Activity simulates daily activities of injured people in a disaster;
 * Some people are too injured to go to the hospital and just stay home (with certain probability)
 * All injured people go to sleep in the evening
 *
 * @author Tom Schons
 */
public class InjuredPopulationActivityMovement extends MapBasedMovement implements SwitchableMovement {

	// Constants for importing settings from default settings file
	public static final String DAY_LENGTH = "dayLength";
	public static final String NUMBER_OF_DAYS = "nbrOfDays";
	public static final String SLEEPING_TIME_MIN = "sleepingTimeMin";
	// Seed used for initializing the scenario's random number generator -> The same seed will always produce the same output which is mandatory for creating reproducable results!
	public static final String RNGSEED = "rngSeed";

	// Probability loaded via external default settings file (if provided)
	// Probability that we are to injured to go to the hospital on our own
	public static final String PROBABILITY_TO_BE_TOO_INJURED_FOR_HOSPITAL = "tooInjuredForHospitalProb";
	
	// Location files loaded via external default settings file (if provided)
	public static final String HOSPITAL_LOCATION_FILE_SETTING = "hospitalLocationFile";
	public static final String HOME_LOCATION_FILE_SETTING = "homeLocationFile";
	
	// Length of the day in seconds
	private double dayLength; 
	// Number of days
	private int nbrOfDays; 
	// Seed used for initializing the activity's random number generator such that the same seed will always produce the same output! 
	private long rngSeed; 
	 // Maximum sleeping time an individual has (in seconds)
	private double sleepingTimeMin; 
	
	// Probability to be too injured to make it to the hosptial, loaded via settings file (if provided)
	private double tooInjuredForHospitalProb;
	
	// File names of the specfic location files
	private String hospitalLocationFile = null;
	private String homeLocationFile = null;
	
	// Lists of coordinates for specific location files
	private List<Coord> hospital = null;
	private List<Coord> homes = null; 
	
	// Holds the nodes currently chosen hospital location - Randomly chosen in order to hold account to invariances in the real world
	private Coord someHospitalLocation;
	// Holds the nodes permanently chosen home location - Randomly chosen in order to hold account to invariances in the real world
	private Coord someHomeLocation;
	
	// The mode in which we are operating this activity right now
	private int mode;
	
	// Modes of operation
	// STAY_AT_HOME -> Is used right after the disaster occurred, we are at home and injured 
	private static final int STAY_AT_HOME = 0;
	// GO_TO_HOSPITAL -> Is used by the node in order to go to a hospital (if not too injured, determined by PROBABILITY_TO_BE_TOO_INJURED_FOR_HOSPITAL)
	private static final int GO_TO_HOSPITAL = 1; 
	// STAY_AT_HOSPITAL -> Node is staying at the hospital for some time after it arrived there (e.g. we are injured and thus see a doctor)
	private static final int STAY_AT_HOSPITAL = 2;
	// GO_HOME -> Nodes go home after being at the hospital (since we assume that not all nodes could see a doctor due to hospitals being overwhelmed nodes try again the next day) 
	private static final int GO_HOME = 3; 
	// TO_ILL_TO_GO_TO_HOSPITAL -> Individual is too injured to go to a hospital (determined by PROBABILITY_TO_BE_TOO_INJURED_FOR_HOSPITAL) 
	private static final int TO_ILL_TO_GO_TO_HOSPITAL = 4;
	// IDLE_MODE -> Used when we returned home but it's "too early" to switch back to sleep mode 
	private static final int IDLE_MODE = 5;
	
	// DijkstraPathFinder used for calculating our path 
	private DijkstraPathFinder pathFinder;
	
	// The exact timeslot when we (re-)started this activtiy 
	private double startedActivityTime;
	
	// LastLocation holds the last location a node visited (is initially set to some home location after finishing the constructor, see below - will always be updated)
	private Coord lastLocation;
	// NextLocation holds the next location a node will visit (will always be updated)
	private Coord nextLocation;  

	// To be set true if we're ready to start this activity --> Can be called from other classes via .Activate() function
	// Since we assume everybody in the disaster zone starts with sleeping this is set false by default here!
	private boolean start = false;
	
	 // To be set true if we're done with this activity  ---> Status can be requested via .isReady() function
	private boolean ready = false; 
	
	// Random generator initalized with seed (if provided via settings file)
	private Random rand;
	
	// Local day counter 
	private int dayCounter; 
	
	// Is set true if we've been to the hospital once today (is reset at the end of every day) 
	private boolean beenToHospital = false;
	
	// Is set true if we're unable to go to hospital (is only set once and then holds the same value during the entire simulation)
	private boolean unableToGoToHospital = false;
	
	// Current time we still need to wait
	private double waitingTime = 0; 
	
	/**
	 * InjuredPopulationActivityMovement constructor
	 * @param settings
	 */
	public InjuredPopulationActivityMovement(Settings settings) {
		super(settings);
		pathFinder = new DijkstraPathFinder(null);
		
		// Loading settings via default settings file
		if (settings.contains(DAY_LENGTH)) {
			this.dayLength = settings.getDouble(DAY_LENGTH);
		}
		else {
			System.out.println("You didn't specify a value for the day length!");
			System.out.println("dayLength: " + this.dayLength); 
		} 
		if (settings.contains(NUMBER_OF_DAYS)) {
			this.nbrOfDays = settings.getInt(NUMBER_OF_DAYS);
		}
		else {
			System.out.println("You didn't specify a value for the number of days!");
			System.out.println("nbrOfDays: " + this.nbrOfDays); 
		}
		if (settings.contains(RNGSEED)) {
			this.rngSeed = settings.getLong(RNGSEED);
		}
		else {
			System.out.println("You didn't specify a value as a seed for the random number generator!");
			System.out.println("rngSeed: " + this.rngSeed); 
		}
		if (settings.contains(PROBABILITY_TO_BE_TOO_INJURED_FOR_HOSPITAL)) {
			this.tooInjuredForHospitalProb = settings.getDouble(PROBABILITY_TO_BE_TOO_INJURED_FOR_HOSPITAL);
		}
		else {
			System.out.println("You didn't specify a value for the probability to be to injured to go to hospital!");
			System.out.println("tooInjuredForHospitalProb: " + this.tooInjuredForHospitalProb); 
		}
		if (settings.contains(SLEEPING_TIME_MIN)) {
			this.sleepingTimeMin = settings.getDouble(SLEEPING_TIME_MIN);
		}
		else {
			System.out.println("You didn't specify a value for the minimum sleeping time!");
			System.out.println("sleepingTimeMin: " + this.sleepingTimeMin); 
		}

		// Loading location files as specified via default settings file (has to be done via try-catch)
		try {	
			if (settings.contains(HOSPITAL_LOCATION_FILE_SETTING)) {
				this.hospitalLocationFile = settings.getSetting(HOSPITAL_LOCATION_FILE_SETTING);
			}
			else {
				System.out.println("You didn't specify a value for the hospital location file!");
				System.out.println("hospitalLocationFile: " + this.hospitalLocationFile); 
			}
			if (settings.contains(HOME_LOCATION_FILE_SETTING)) {
				this.homeLocationFile = settings.getSetting(HOME_LOCATION_FILE_SETTING);
			}
			else {
				System.out.println("You didn't specify a value for the hospital location file!");
				System.out.println("homeLocationFile: " + this.homeLocationFile); 
			}
		} catch (Throwable t) {
			System.out.println("Reading the location files somehow failed - did you specify the correct path?"); 
		}
		
		// Create a new random generator for this activity with the provided seed -> Important for ensuring reproducable results! 
		this.rand = new Random(this.rngSeed);  
		
		// Set day counter to 0 since we start our simulation at day 0 
		this.dayCounter = 0;
		
		// Set unableToGoToHospital variable once and for all for the entire simulation period, based on tooInjuredForHospitalProb provided via default settings file
		if ((this.getRandomDouble() * this.tooInjuredForHospitalProb) <= 0.15) {
			// We're unable to go to the hospital on our own for the rest of this simulation
			this.unableToGoToHospital = true; 
		}

		// Reading specific locations from the map files provided via default settings file
		SimMap map = getMap();
		Coord offset = map.getOffset();
		
		// Read hospital locations into local array
		this.hospital = new LinkedList<Coord>();
		try {
		List<Coord> locationsRead1 = (new WKTReader()).readPoints(new File(hospitalLocationFile));
		for (Coord coord1 : locationsRead1) {
			// Mirroring all points if map data is mirrored
			if (map.isMirrored()) {
				coord1.setLocation(coord1.getX(), -coord1.getY());
			}
			coord1.translate(offset.getX(), offset.getY());
			this.hospital.add(coord1);
		}
		// Chose a random position for the hospital location
		int firstRandom = this.getRandom(0,this.hospital.size()-1);
		this.someHospitalLocation = this.hospital.get(firstRandom).clone(); 
		} catch (Throwable t) {
			System.out.println("Reading the hospital location file permanently failed");
			System.out.println("someHospitalLocation " + this.someHospitalLocation);
		}
		
		// Read home locations into local array 
		this.homes = new LinkedList<Coord>();
		try {
		List<Coord> locationsRead2 = (new WKTReader()).readPoints(new File(homeLocationFile));
		for (Coord coord2 : locationsRead2) {
			// Mirroring all points if map data is mirrored
			if (map.isMirrored()) {
				coord2.setLocation(coord2.getX(), -coord2.getY());
			}
			coord2.translate(offset.getX(), offset.getY());
			this.homes.add(coord2);
		}
		// Chose a random position for the home location 
		int secondRandom = this.getRandom(0,this.homes.size()-1);
		this.someHomeLocation = this.homes.get(secondRandom).clone();
		} catch (Throwable t) {
			System.out.println("Reading the home location file permanently failed");
			System.out.println("someHomeLocation " + this.someHomeLocation);
		}
		
		// Set fixed home location of the injured node
		this.lastLocation = this.someHomeLocation.clone(); 
		
		// Set initial mode
		this.mode = STAY_AT_HOME; 
		// Value of -1 means we haven't started yet
		this.startedActivityTime = -1;
	}

	/**
	 * Construct a new InjuredPopulationActivityMovement instance from a prototype
	 * @param proto
	 */
	public InjuredPopulationActivityMovement(InjuredPopulationActivityMovement prototype) {
		super(prototype);
		this.pathFinder = prototype.pathFinder;
		
		// Loading settings via default settings file
		this.rand = prototype.getRand(); 
		this.dayLength = prototype.getDayLength(); 
		this.nbrOfDays = prototype.getNbrOfDays(); 
		this.homes = prototype.getHomes(); 
		this.hospital = prototype.getHospitals(); 
		this.sleepingTimeMin = prototype.getSleepingTimeMin(); 
		this.tooInjuredForHospitalProb = prototype.getTooInjuredForHospitalProb(); 
		
		// Chose a random position for the hospital location
		int firstRandom = this.getRandom(0,this.hospital.size()-1);
		this.someHospitalLocation = this.hospital.get(firstRandom).clone(); 
		
		// Chose a random position for the home location
		int secondRandom = this.getRandom(0,this.homes.size()-1);
		this.someHomeLocation = this.homes.get(secondRandom).clone();
		
		// Set day counter to 0 since we start our simulation at day 0 
		this.dayCounter = 0;
		
		// Set unableToGoToHospital variable once and for all for the entire simulation period, based on tooInjuredForHospitalProb provided via default settings file
		if ((this.getRandomDouble() * this.tooInjuredForHospitalProb) <= 0.15) {
			// We're unable to go to the hospital on our own for the rest of this simulation
			this.unableToGoToHospital = true; 
		}
		
		// Set fixed home location of the injured node
		this.lastLocation = this.someHomeLocation.clone(); 
		
		// Set initial mode
		this.mode = STAY_AT_HOME; 
		// Value of -1 means we haven't started yet
		this.startedActivityTime = -1;
	}
	 
	@Override
	public Path getPath() {
		// We only run into this if clause if a) the simulation has started b) our activity is activated right now c) the simulation hasn't reached it's end yet
		if ((SimClock.getTime() > 0) && (start) && (this.dayCounter != this.nbrOfDays)) {
		switch (mode) {
			case STAY_AT_HOME: {
				// STAY_AT_HOME -> Is used right after the disaster occurred, we are at home and injured 
				if (startedActivityTime == -1) {
					// We just (re-)started this activity
					// Generating a home waiting time
					this.waitingTime = generateHomeWaitTime(); 
					// Saving the exact timeslot we started this activity 
					this.startedActivityTime = SimClock.getTime();
				}
				if (SimClock.getTime() >= (this.startedActivityTime + this.waitingTime)) {
					// We're done waiting, we can now switch to the next mode (GO_TO_HOSPITAL)
					this.mode = GO_TO_HOSPITAL; 
					System.out.println("We are done waiting -> going to hospital!");
					break; 
				}
				else {
					// We still need to idle since we haven't waited enough
					System.out.println(" - STAY_AT_HOME mode - Active - Waiting"); 
					break; 
				}
			}
			case GO_TO_HOSPITAL: {
				// GO_TO_HOSPITAL -> Is used by the node in order to go to a hospital (if not too injured, determined by PROBABILITY_TO_BE_TOO_INJURED_FOR_HOSPITAL)
				if (!unableToGoToHospital) {
					// We are able to go to the hospital on our own 
					if (!beenToHospital) {
							// We haven't been at the hospital for today - so let's go there
							// Chosing a random hospital we want to go to
							int firstRandom = this.getRandom(0,this.hospital.size()-1);
							// Setting nextLocation to hospital location
							this.nextLocation = this.hospital.get(firstRandom).clone();
							this.beenToHospital = true;
							this.mode = STAY_AT_HOSPITAL;

						// Now we can calculate the PATH to go to that specific hospital	
						System.out.println("Calculating PATH to go to hospital");
					
						// Calculation of path to hospital
						SimMap map = super.getMap();
						if (map == null) {
							System.out.println("Error while getting map!");
							return null;
						}
						
						// Creating the path 
						Path path = new Path(generateSpeed());
						try {
							// From Home -> To Hospital
							MapNode fromNode = map.getNodeByCoord(lastLocation); // Home
							MapNode toNode = map.getNodeByCoord(nextLocation); // Hospital 
							List<MapNode> nodePath = pathFinder.getShortestPath(fromNode, toNode);
			
							for (MapNode node : nodePath) {
								path.addWaypoint(node.getLocation());
								}
						}
						catch (Throwable t)
						{
							System.out.println("Error while creating the path!");
							t.printStackTrace();
						}
						
						// Ensuring that the last location is always updated after we finish the path creation method
						this.lastLocation = nextLocation.clone();
						
						// Calculating a waiting time to be sure that we don't arrive "too early" at our hospital location! (in case the hospital and the home location would be next door)
						this.startedActivityTime = SimClock.getTime();
						this.waitingTime = generateHomeWaitTime(); 
						
						System.out.println("Generated following PATH from HOME to Hospital: " + path);
						
						return path;
					}
				}
				else {
					// We're not able to go to the hospital on our own!  -> We can now switch to the next mode (TO_ILL_TO_GO_TO_HOSPITAL)
					this.mode = TO_ILL_TO_GO_TO_HOSPITAL; 
					System.out.println("We are to ill to go to the hospital -> TO_ILL_TO_GO_TO_HOSPITAL!");
					// Create a larger waiting time since we just idle in this mode
					this.waitingTime = generateHospitalWaitTime() * 2; 
					this.startedActivityTime = SimClock.getTime(); 
					}
				}
			case STAY_AT_HOSPITAL: {
				// STAY_AT_HOSPITAL -> Node is staying at the hospital for some time after it arrived there (e.g. we are injured and thus see a doctor)
				if (SimClock.getTime() >= (this.startedActivityTime + this.waitingTime)) {
					// We are sure that we arrived at the hospital, no preparing for leaving later on 
					// Create a larger waiting time since we assume we stay at the hospital for at least some time
					this.waitingTime = generateHospitalWaitTime(); 
					System.out.println("Generated a STAY_AT_HOSPITAL waiting time at the hospital of : " + this.waitingTime);
					// Switching to GO_HOME mode 
					this.mode = GO_HOME;
					System.out.println("We will wait at the hospital for " + this.waitingTime + " and then -> GO_HOME!");
					this.startedActivityTime = SimClock.getTime(); 
				}
				else {
					// We still need to idle at the hospital
					System.out.println(" - STAY_AT_HOSPITAL mode - Active - Waiting"); 
					break; 
				}
			}
			case GO_HOME: {
				// GO_HOME -> Nodes go home after being at the hospital (since we assume that not all nodes could see a doctor due to hospitals being overwhelmed nodes try again the next day) 
				if (SimClock.getTime() >= (this.startedActivityTime + this.waitingTime)) {
					// Now we are sure that we waited long enough at the hospital location -> Go home and then sleep there 
					
					// Set beenToHospital to false such that we are able to go back to the hospital tomorrow 
					this.beenToHospital = false; 
					
					// Now we can calculate the PATH to go back home	
					System.out.println("Calculating PATH to go back home");
					
					// Calculation of path back home
					SimMap map = super.getMap();
					if (map == null) {
						System.out.println("Error while getting map!");
							return null;
					}
					
					// Going back to the home location
					this.nextLocation = someHomeLocation.clone(); 
					
					// Creating the path 
					Path path = new Path(generateSpeed());
					try {
						// From Hospital -> To Home
						MapNode fromNode = map.getNodeByCoord(lastLocation); // Hospital
						MapNode toNode = map.getNodeByCoord(nextLocation); // Home
						List<MapNode> nodePath = pathFinder.getShortestPath(fromNode, toNode);
		
						for (MapNode node : nodePath) {
							path.addWaypoint(node.getLocation());
							}
					}
					catch (Throwable t)
					{
						System.out.println("Error while creating the path!");
						t.printStackTrace();
					}
					
					// Ensuring that the last location is always updated after we finish the path creation method
					this.lastLocation = nextLocation.clone();
					this.mode = IDLE_MODE; 
					
					// Calculating a waiting time to be sure that we don't arrive "too early" at our home location! (in case the home and the hospital location would be next door)
					this.waitingTime = generateHomeWaitTime();
					this.startedActivityTime = SimClock.getTime(); 
					System.out.println("Going home after hospital!");
					System.out.println("Generated following PATH from Hospital to HOME: " + path);
					return path;
				}
				else {
					// We still need to idle at the hospital
					System.out.println(" - GO_HOME mode - Active - Waiting"); 
					break; 
				}
			}
			case IDLE_MODE: {
				// IDLE_MODE -> Used when we returned home but it's "too early" to switch back to sleep mode 
				if ((SimClock.getTime() >= (this.startedActivityTime + this.waitingTime)) && (SimClock.getTime() > (this.dayLength*(this.dayCounter+1)))) {
					// We can continue if a) the actual SimClock time is greater than the old startedActivtyTime plus the current waiting time and
					// b) the actual SimClock time is greater than the (dayLength) times (the dayCounter +1), which means we successfully simulated one more day
					// we are done ideling -> now we can safely switch to sleep activity 
					
					// Reset parameteres in order to restart injured activity next day
					this.ready = true; 
					this.start = false; 
					this.mode = STAY_AT_HOME; 
					this.startedActivityTime = -1; 
					System.out.println(" - IDLE_MODE mode - Should now be over - Switching to sleep activity now");
				}
				else {
					// We still need to idle as it's to early to go to sleep again
					System.out.println(" - IDLE_MODE mode - Active - Waiting"); 
					break; 
				}
			}
			case TO_ILL_TO_GO_TO_HOSPITAL: {
				// TO_ILL_TO_GO_TO_HOSPITAL -> Individual is too injured to go to a hospital (determined by PROBABILITY_TO_BE_TOO_INJURED_FOR_HOSPITAL) 
				if (unableToGoToHospital) {
					// Should actually always be true when we specifically chose to enter this mode (if everything went fine)  
					// We're not able to go to hospital on our own!
					if ((SimClock.getTime() >= (this.startedActivityTime + this.waitingTime)) && (SimClock.getTime() > (this.dayLength*(this.dayCounter+1)))) {
						// Basically we're done waiting, but since there is nothing more to do we continue to wait
						System.out.println("Generated a waiting time in TO_ILL_TO_GO_TO_HOSPITAL mode - forever - of " + waitingTime);
						this.startedActivityTime = SimClock.getTime(); 
						ready = true; 
					}
				}
			}
		   }
		}
		return null;
	}
	
	@Override
	public Coord getInitialLocation() {
		// We initially start being at home
		return someHomeLocation.clone();
	}
	
	protected double generateHospitalWaitTime() {
		// We generate the waiting time we want to spent at the hospital 
		double tmpWaitTime = this.getRandomDouble()*20000;
		System.out.println("Generated a - generateHospitalWaitTime() - INJURED POPULATION - AT HOSPITAL - waiting time of: " + tmpWaitTime); 
		return tmpWaitTime; 
	}
	
	protected double generateHomeWaitTime() {
		// We generate the waiting time we want to spent at home
		double tmpWaitTime = this.getRandomDouble()*2000;
		System.out.println("Generated a - generateHomeWaitTime() - INJURED POPULATION - AT HOME - waiting time of: " + tmpWaitTime); 
		return tmpWaitTime; 
	}
	
	@Override
	protected double generateWaitTime() {
		// Since our movement model is more or less fixed by the parameters loaded from the external default settings file we don't really need this function
		// Especially since generateWaitTime() is called by internal functions of the ONE after each getPath() method has returned, so we just use this here as a simple step counter
		// The reason is that we don't need this function in the injured activity, yet we can make use of it to step trough our programm 
		// The value of 100 is arbitrary, yet values shouln't be too big or small in order that the ONE operates properly
		return 100;
	}
	
	public Coord getLastLocation() {
		return lastLocation.clone();
	}
	
	public void setLocation(Coord location) {
		this.lastLocation = location; 
	}
	
	public Coord getHomeLocation() {
		return someHomeLocation.clone();
	}

	public Coord getLocation() {
		return lastLocation.clone();
	}
	
	public List<Coord> getHomes()
	{
		return this.homes; 
	}
	
	public List<Coord> getHospitals()
	{
		return this.hospital; 
	}
	
	// Get random int value, between provided min and max; returns 0 if invalid argument is provided 
	public int getRandom(int min, int max) {
		if ((min >= 0) && (max > 0)) {
			return this.rand.nextInt((max - min) + min);
		}
		return 0; 
	}

	// Get random double value, between 0.0 and 1.0
	public double getRandomDouble() {
		return this.rand.nextDouble(); 
	}
	
	// Return our random generator 
	public Random getRand() {
		return this.rand; 
	}
	
	public double getDayLength() {
		return this.dayLength; 
	}
	
	public int getNbrOfDays() {
		return this.nbrOfDays; 
	}
	
	public double getTooInjuredForHospitalProb() {
		return this.tooInjuredForHospitalProb;
	}
	
	public double getSleepingTimeMin() {
		return this.sleepingTimeMin; 
	}
	
	// Function for (re-) activating our injured mode
	// Function is to be called at the end of any other activity such that we can (re-) activate the injured mode
	public void Activate(int dayCounter) {
		this.dayCounter = dayCounter; 
		// True -> means we can start this activity at anytime as of now
		start = true;
		// False -> means we're not yet finished with this activity
		ready = false;
	}
	
	// Returns false if we haven't finished yet
	// Returns true if we are done with our injured activity so other activities know that we are ready to switch back such that they can proceed with their operations
	public boolean isReady() {
		return this.ready; 
	}
}
