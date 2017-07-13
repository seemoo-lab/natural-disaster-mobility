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
import java.util.ArrayList;
import movement.map.SimMap;
import java.util.Random;
import core.SimClock;

/**
 * The ScientificActivityMovement class representing the movement of scientits witin a disaster area
 * 
 * When natural disasters occur there are often international scientists around the area collecting scientific evidences
 * Sometimes this is even done before a disaster occures (when the disaster is foreseeable),
 * yet sometimes scientists can only arrive in the aftermath of the disaster when the disaster is not foreseeable.
 * Some scientists usually volunteer for helping the incoming disaster relief organizations with their relief work before leaving the country
 * All scientists usually go home to sleep in the evening
 *
 * @author Tom Schons
 */
public class ScientificActivityMovement extends MapBasedMovement  implements SwitchableMovement {

	// Constants for importing settings from default settings file
	public static final String DAY_LENGTH = "dayLength";
	public static final String NUMBER_OF_DAYS = "nbrOfDays";
	public static final String SLEEPING_TIME_MIN = "sleepingTimeMin";
	// Seed used for initializing the scenario's random number generator -> The same seed will always produce the same output which is mandatory for creating reproducable results!
	public static final String RNGSEED = "rngSeed";
	// Number of places to be visited
	public static final String PLACES_TO_VISIT = "placesToVisit"; 
	
	// Probability loaded via external default settings file (if provided)
	// Probability that we volunteer for helping the incoming disaster relief organizations with cleaning the streets
	// NOTE: If we don't do this, we will leave the country earlier (e.g. switiching to the "go to airport" activity
	public static final String PROBABILITY_TO_VOLUNTEER_FOR_DISASTER_RELIEF = "reliefVolunteeringProb";

	// Location files loaded via external default settings file (if provided)
	public static final String HOME_LOCATION_FILE_SETTING = "homeLocationFile";
	public static final String MAIN_POINT_LOCATION_FILE_SETTING = "mainPointLocationFile";
	
	// Length of the day in seconds
	private double dayLength; 
	// Number of days
	private int nbrOfDays; 
	// Seed used for initializing the activity's random number generator such that the same seed will always produce the same output! 
	private long rngSeed; 
	 // Maximum sleeping time an individual has (in seconds)
	private double sleepingTimeMin; 
	
	// Probability to volunteer for helping disaster relief units with their work (if provided)
	private double reliefVolunteeringProb;
	
	// Number of volunteering places we visit
	private double placesToVisit; 
	// Volunteering or patrolling places actually visited so far
	private double placesCount = 0; 
	
	// File names of the specific location files
	private String homeLocationFile = null;
	private String mainPointLocationFile = null;
	
	// Lists of coordinates for specific location files
	private List<Coord> homes = null; 
	private List<Coord> mainPoints = null; 
	
	// Holds the nodes permanently chosen home location - Randomly chosen in order to hold account to invariances in the real world
	private Coord someHomeLocation;
	// Holds the nodes currently chosen point location - can be any place he want's to go to on the map
	private Coord somePointLocation;
	
	// The mode in which we are operating this activity right now
	private int mode; 
	
	// Modes of operation
	// COLLECT_EVIDENCE -> Collecting scientific evidences, moving around the disaster area 
	private static final int COLLECT_EVIDENCE = 1;
	// VOLUNTEER -> Volunteering for helping disaster relief organizations with certain probability
	private static final int VOLUNTEER = 2;
	// GO_HOME -> Nodes go home after being in the city all day
	private static final int GO_HOME = 3;
	// IDLE_MODE -> Used when we returned home but it's "too early" to switch back to sleep mode 
	private static final int IDLE_MODE = 4;
	
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
	
	// To be set true if we want to go to the airport and leave the country 
	private boolean goToAirport = false; 
	
	// Random generator initalized with seed (if provided via settings file)
	private Random rand;
	
	// Local day counter 
	private int dayCounter; 
	
	// Current time we still need to wait
	private double waitingTime = 0; 
	
	// Calculates with certain probability (if provided via settings file) wheather or not we'll help incoming disaster relief organizations 
	private boolean reliefVolunteering = false; 

	/**
	 * ScientificActivityMovement constructor
	 * @param settings
	 */
	public ScientificActivityMovement(Settings settings) {
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
		if (settings.contains(PLACES_TO_VISIT)) {
			this.placesToVisit = settings.getDouble(PLACES_TO_VISIT);
		}
		else {
			System.out.println("You didn't specify a value for the amount of places we should visit!");
			System.out.println("placesToVisit: " + this.placesToVisit); 
		}
		if (settings.contains(SLEEPING_TIME_MIN)) {
			this.sleepingTimeMin = settings.getDouble(SLEEPING_TIME_MIN);
		}
		else {
			System.out.println("You didn't specify a value for the minimum sleeping time!");
			System.out.println("sleepingTimeMin: " + this.sleepingTimeMin); 
		}
		if (settings.contains(PROBABILITY_TO_VOLUNTEER_FOR_DISASTER_RELIEF)) {
			this.reliefVolunteeringProb = settings.getDouble(PROBABILITY_TO_VOLUNTEER_FOR_DISASTER_RELIEF);
		}
		else {
			System.out.println("You didn't specify a value for the probability to volunteer to help disaster relif units!");
			System.out.println("reliefVolunteeringProb: " + this.reliefVolunteeringProb); 
		}
		
		// Loading location files as specified via default settings file (has to be done via try-catch)
		try {	
			if (settings.contains(HOME_LOCATION_FILE_SETTING)) {
				this.homeLocationFile = settings.getSetting(HOME_LOCATION_FILE_SETTING);
			}
			else {
				System.out.println("You didn't specify a value for the home location file!");
				System.out.println("homeLocationFile: " + this.homeLocationFile); 
			}
			if (settings.contains(MAIN_POINT_LOCATION_FILE_SETTING)) {
				this.mainPointLocationFile = settings.getSetting(MAIN_POINT_LOCATION_FILE_SETTING);
			}
			else {
				System.out.println("You didn't specify a value for the main points location file!");
				System.out.println("mainPointLocationFile: " + this.mainPointLocationFile); 
			}
		} catch (Throwable t) {
			System.out.println("Reading the location files somehow failed - did you specify the correct path?"); 
		}
		
		// Create a new random generator for this activity with the provided seed -> Important for ensuring reproducable results! 
		this.rand = new Random(this.rngSeed);  
		
		// Set day counter to 0 since we start our simulation at day 0 
		this.dayCounter = 0;
		
		// Set reliefVolunteering variable once and for all for the entire simulation period, based on reliefVolunteeringProb provided via default settings file
		if (((this.getRandomDouble() * this.reliefVolunteeringProb) * 2) < 0.5) {
			// We'll volunteer to help disaster relief organizations with their work after we're done with our scientific activities
			this.reliefVolunteering = true; 
		}
		
		// Reading specific locations from the map files provided via default settings file
		SimMap map = getMap();
		Coord offset = map.getOffset();
		
		// Read main street points locations into local array
		this.mainPoints = new LinkedList<Coord>();
		try {
		List<Coord> locationsRead1 = (new WKTReader()).readPoints(new File(mainPointLocationFile));
		for (Coord coord1 : locationsRead1) {
			// Mirroring all points if map data is mirrored
			if (map.isMirrored()) {
				coord1.setLocation(coord1.getX(), -coord1.getY());
			}
			coord1.translate(offset.getX(), offset.getY());
			this.mainPoints.add(coord1);
		}
		// Chose a random position for a main street point location
		int firstRandom = this.getRandom(0,this.mainPoints.size()-1);
		this.somePointLocation = this.mainPoints.get(firstRandom).clone();
		} catch (Throwable t) {
			System.out.println("Reading the main points location file permanently failed");
			System.out.println("somePointLocation " + this.somePointLocation);
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
		
		// Set fixed home location of the scientific node
		this.lastLocation = this.someHomeLocation.clone(); 
		
		// Set initial mode
		this.mode = COLLECT_EVIDENCE;
		// Value of -1 means we haven't started yet
		this.startedActivityTime = -1;
	}

	/**
	 * Construct a new ScientificActivityMovement instance from a prototype
	 * @param proto
	 */
	public ScientificActivityMovement(ScientificActivityMovement prototype) {
		super(prototype);
		this.pathFinder = prototype.pathFinder;
		
		// Loading settings via default settings file
		this.rand = prototype.getRand(); 
		this.dayLength = prototype.getDayLength(); 
		this.nbrOfDays = prototype.getNbrOfDays(); 
		this.placesToVisit = prototype.getPlacesToVisit(); 
		this.sleepingTimeMin = prototype.getSleepingTimeMin(); 
		this.mainPoints = prototype.getMainPoints(); 
		this.homes = prototype.getHomes(); 
		
		// Chose a random position for the homes location
		int firstRandom = this.getRandom(0,this.homes.size()-1);
		this.someHomeLocation = this.homes.get(firstRandom).clone();

		// Chose a random position for the main points location
		int secondRandom = this.getRandom(0,this.mainPoints.size()-1);
		this.somePointLocation = this.mainPoints.get(secondRandom).clone();
		
		// Set day counter to 0 since we start our simulation at day 0 
		this.dayCounter = 0;
		
		// Set fixed home location of the scientific node
		this.lastLocation = this.someHomeLocation.clone(); 
		
		// Set initial mode
		this.mode = COLLECT_EVIDENCE; 
		// Value of -1 means we haven't started yet
		this.startedActivityTime = -1;
	}

	@Override
	public Path getPath() {
		// We only run into this if clause if a) the simulation has started b) our activity is activated right now c) the simulation hasn't reached it's end yet
		if ((SimClock.getTime() > 0) && (start) && (this.dayCounter != this.nbrOfDays)) {
		switch (mode) {
			case COLLECT_EVIDENCE: {
				// COLLECT_EVIDENCE -> Collecting scientific evidences, moving around the disaster area 
				if (startedActivityTime == -1) {
					// We just (re-)started this activity
					// Generating a home waiting time
					this.waitingTime = generateHomeWaitTime(); 
					// Saving the exact timeslot we started this activity 
					this.startedActivityTime = SimClock.getTime();
				}
				if (SimClock.getTime() >= (this.startedActivityTime + this.waitingTime)) {
					// We're done waiting, we can now go collect evidences within the area
					if (this.placesToVisit != this.placesCount) {
						// Increasing the count of places we already visited for collecting evidences
						this.placesCount++; 
						System.out.println("We are done waiting -> we now go collecting evidences within the area");
	
						// Now we can calculate the PATH to the next main point location
						System.out.println("Calculating PATH to go collecting scientific evidences");
						
						// Selecting a main point location for the collection of scientific evidences 
						int firstRandom = this.getRandom(0,this.mainPoints.size()-1);
						// Setting nextLocation to mainPoints location
						this.nextLocation = this.mainPoints.get(firstRandom).clone();
						
						// Calculation of path to the sientific evidences location
						SimMap map = super.getMap();
						if (map == null) {
							System.out.println("Error while getting map!");
							return null;
						}
						// Creating the path 
						Path path = new Path(generateSpeed());
						try {
							// From Home -> To selected main point location
							MapNode fromNode = map.getNodeByCoord(lastLocation); // Actual location
							MapNode toNode = map.getNodeByCoord(nextLocation); // Volunteering place 
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
						
						// Calculating a waiting time to be sure that we don't arrive "too early"
						this.startedActivityTime = SimClock.getTime();
						this.waitingTime = generateHomeWaitTime(); 
						
						System.out.println("Generated following PATH to scientific evidence collecting location: " + path);
	
						return path;
					}
					else {
						// we visited enough places for evidence collection, now go home and sleep
						this.mode = GO_HOME;
						System.out.println("We visited enough places for evidence collection - Switching to GO_HOME mode"); 
						// Calculating a waiting time to be sure that we don't arrive "too early"
						this.startedActivityTime = SimClock.getTime();
						this.waitingTime = generateHomeWaitTime(); 
						break; 
					}
				}
				else {
					// We still need to idle since we haven't waited enough
					System.out.println(" - COLLECT_EVIDENCE mode - Active - Waiting"); 
					break; 
				}
			}
			case GO_HOME: {
				// GO_HOME -> Nodes go home after being in the city all day
				if (SimClock.getTime() >= (this.startedActivityTime + this.waitingTime)) {
					// Now we are sure that we waited long enough -> Go home and then sleep there 
					
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
						// From city -> To Home
						MapNode fromNode = map.getNodeByCoord(lastLocation); // Actual location
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
					
					// Calculating a waiting time to be sure that we don't arrive "too early" at our home location!
					this.waitingTime = generateHomeWaitTime();
					this.startedActivityTime = SimClock.getTime(); 
					System.out.println("Going home!");
					System.out.println("Generated following PATH to HOME: " + path);
					return path;
				}
				else {
					// We still need to idle a bit
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
					
					// Checking weather to volunteer or to go home the next day
					// Calculation implies that probability for stopping scientific activities increases with the number of days spent in the disaster area
					if ((this.getRandomDouble() / this.dayCounter + 1) < 0.5)
					{
						// We're going to volunteer or leave the disaster area via the airport tomorrow, based on PROBABILITY_TO_VOLUNTEER_FOR_DISASTER_RELIEF
						if (this.reliefVolunteering)
						{
							// We're volunterring for disaster relief as of tomorrow
							this.mode = VOLUNTEER; 
							// Reset places count to 0 so that we can restart the activity tomorrow 
							this.placesCount = 0; 
						}
						else
						{
							// We're leaving the disaster area via the airport as of tomorrow
							// We switch to the "go to airport" activity and then leave the disater area 
							this.goToAirport = true; 
							System.out.println("Going back to the airport and then heading home!");
						}
					}
					else
					{
						// We're continuing our scientific evidence collection as of tomorrow 
						// Reset places count to 0 so that we can restart the activity tomorrow 
						this.placesCount = 0; 
						// Reset mode such that activity can restart tomorrow 
						this.mode = COLLECT_EVIDENCE; 
					}
					
					// Reset parameteres in order to restart scientific activity the next day
					this.ready = true; 
					this.start = false; 

					this.startedActivityTime = -1; 
					System.out.println(" - IDLE_MODE mode - Should now be over - Switching to sleep activity now");
				}
				else {
					// We still need to idle as it's to early to go to sleep again
					System.out.println(" - IDLE_MODE mode - Active - Waiting"); 
					break; 
				}
				if (this.nbrOfDays == this.dayCounter + 2) {
					// We're leaving the disaster area via the airport as of tomorrow
					// We switch to the "go to airport" activity and then leave the disater area 
					// Ensuring that all scientists leave on the last day of the disaster
					this.placesCount = this.placesToVisit;
					this.goToAirport = true;
					System.out.println("Going back to the airport and then heading home!");
				}
			}
			case VOLUNTEER: {
				// VOLUNTEER -> Volunteering for helping disaster relief organizations with certain probability (which increases with the amount of days passed by)
				// In VOLUNTEER mode we walk all accross the city 
				if (startedActivityTime == -1) {
					// We just (re-)started this activity
					// Generating a home waiting time
					this.waitingTime = generateHomeWaitTime(); 
					// Saving the exact timeslot we started this activity 
					this.startedActivityTime = SimClock.getTime();
				}
				if (SimClock.getTime() >= (this.startedActivityTime + this.waitingTime)) {
					// We waited long enough, we are now going to help disaster relief organizations with their cleaning works
					
					if (this.placesToVisit != this.placesCount) {
						// Increasing the count of places we already visited
						this.placesCount++; 
						
						// Now we can calculate the PATH to go to one volunteering place
						System.out.println("Calculating PATH to go volunteering");
						
						// Calculation of path to volunteering place
						SimMap map = super.getMap();
						if (map == null) {
							System.out.println("Error while getting map!");
								return null;
						}
						
						// The node we want to go to
						MapNode newToNode = null; 
						
						if (this.placesCount == 0) {
							// First round for volunteering, chosing random position on map to go to
							int firstRandom = this.getRandom(0,this.mainPoints.size()-1);
							// Setting nextLocation to mainPoints location
							this.nextLocation = this.mainPoints.get(firstRandom).clone();
							// Map location to node
							newToNode = map.getNodeByCoord(this.nextLocation);
						}
						else {
							// Second round (or more) for volunteering, chosing location close by
							// Finding a neighbor we can go to
							List<MapNode> neighbors = new ArrayList<MapNode>();
							neighbors = map.getNodeByCoord(this.lastLocation).getNeighbors();
							// Chose a new random neighbor to go to 
							int firstRandom = this.getRandom(0,neighbors.size()-1);
							
							// Set toNode location to neighbor location
							newToNode = map.getNodeByCoord(neighbors.get(firstRandom).getLocation().clone());
							this.nextLocation = newToNode.getLocation().clone(); // Neighbor
						}
						
						// Creating the path 
						Path path = new Path(generateSpeed());
						try {
							// To volunteering place
							MapNode fromNode = map.getNodeByCoord(lastLocation); // Actual location
							// Use calculated node from above
							MapNode toNode = newToNode; 
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
						
						// Calculating a waiting time to be sure that we don't arrive "too early" at our volunteering location!
						this.startedActivityTime = SimClock.getTime();
						this.waitingTime = generateVolunteeringWaitTime(); 
						
						System.out.println("Generated following PATH when going volunteering: " + path);
						
						return path;
					}
					else {
						// We're done volunteering -> now go back home
						this.mode = GO_HOME; 
						System.out.println("Going back home");
						this.waitingTime = generateHomeWaitTime(); 
						this.startedActivityTime = SimClock.getTime(); 
					}
				}
				else {
					// We still need to idle when being a volunteer
					System.out.println(" - VOLUNTEER mode - Active - Waiting"); 
					break; 
				}
			}
		  }
	    }
		return null; 
	}
	
	@Override
	protected double generateWaitTime() {
		// Since our movement model is more or less fixed by the parameters loaded from the external default settings file we don't really need this function
		// Especially since generateWaitTime() is called by internal functions of the ONE after each getPath() method has returned, so we just use this here as a simple step counter
		// The reason is that we don't need this function in the scientific activity, yet we can make use of it to step trough our programm 
		// The value of 100 is arbitrary, yet values shouln't be too big or small in order that the ONE operates properly
		return 100;
	}
	
	protected double generateHomeWaitTime() {
		// We generate the waiting time we want to spent at home
		double tmpWaitTime = this.getRandomDouble()*2000;
		System.out.println("Generated a - generateHomeWaitTime() - SCIENTIFIC POPULATION - AT HOME - waiting time of: " + tmpWaitTime); 
		return tmpWaitTime; 
	}
	
	protected double generateVolunteeringWaitTime() {
		// We generate the waiting time we want to spent volunteering
		double tmpWaitTime = this.getRandomDouble()*5000;
		System.out.println("Generated a - generateVolunteeringWaitTime() - SCIENTIFIC POPULATION - waiting time of: " + tmpWaitTime); 
		return tmpWaitTime; 
	}
	
	@Override
	public Coord getInitialLocation() {
		// We initially start being at home
		return someHomeLocation.clone();
	}
	
	public Coord getLastLocation() {
		return lastLocation.clone();
	}

	public List<Coord> getHomes()
	{
		return this.homes; 
	}
	
	public List<Coord> getMainPoints()
	{
		return this.mainPoints; 
	}
	
	public long getSeed() {
		return this.rngSeed; 
	}
	
	public double getPlacesToVisit() {
		return this.placesToVisit; 
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
	
	public double getSleepingTimeMin() {
		return this.sleepingTimeMin; 
	}
	
	public boolean getGoToAirport() {
		return this.goToAirport; 
	}
	
	// Function for (re-) activating our scientific mode
	// Function is to be called at the end of any other activity such that we can (re-) activate the scientific mode
	public void Activate(int dayCounter) {
		this.dayCounter = dayCounter; 
		// True -> means we can start this activity at anytime as of now
		start = true;
		// False -> means we're not yet finished with this activity
		ready = false;
	}

	// Returns false if we haven't finished yet
	// Returns true if we are done with our scientific activity so other activities know that we are ready to switch back such that they can proceed with their operations
	public boolean isReady() {
		return this.ready; 
	}
}
