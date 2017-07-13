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
 * The OfficialsActivityMovement class represents the movement submodel of government or UN officials in a disaster zone
 * 
 * Officials go to the OSOCC, the Town Hall and the base camp on a regular basis for meeting with different parties,
 * such as for example disaster relief organizations and for organizing the disaster relief effords in the aftermath of a natural disaster.
 * Officials go to reconnaissance missions in order to enable the provisioning of help to be best allocated and infrastructural damage and reparation needs to be assessed.
 * Food and water distribution is (partially) organized by the officials in collaboration with disaster relief organizations.
 * Officials usually also coordinate burials outside the city center in order to deal with the problem of overwhelmed morgues.
 * 
 * @author Tom Schons
 */
public class OfficialsActivityMovement extends MapBasedMovement implements SwitchableMovement {

	// Constants for importing settings from default settings file
	public static final String DAY_LENGTH = "dayLength";
	public static final String NUMBER_OF_DAYS = "nbrOfDays";
	// Seed used for initializing the scenario's random number generator -> The same seed will always produce the same output which is mandatory for creating reproducable results!
	public static final String RNGSEED = "rngSeed";
	// Number of places to be visited
	public static final String PLACES_TO_VISIT = "placesToVisit"; 
	
	// Location files loaded via external default settings file (if provided)
	public static final String HOME_LOCATION_FILE_SETTING = "homeLocationFile";
	public static final String MAIN_POINT_LOCATION_FILE_SETTING = "mainPointLocationFile";
	public static final String OSOCC_LOCATIONS_FILE_SETTING = "osoccLocationsFile";
	public static final String BASE_CAMP_LOCATIONS_FILE_SETTING = "baseCampLocationsFile";
	public static final String TOWN_HALL_LOCATIONS_FILE_SETTING = "townHallLocationsFile";
	public static final String FOOD_LOCATION_FILE_SETTINGS = "foodLocationFile";
	public static final String BURIALS_LOCATION_FILE_SETTINGS = "burialsLocationFile";
	public static final String AIRPORT_LOCATIONS_FILE_SETTING = "airportLocationsFile";
	
	// Length of the day in seconds
	private double dayLength; 
	// Number of days
	private int nbrOfDays; 
	// Seed used for initializing the activity's random number generator such that the same seed will always produce the same output! 
	private long rngSeed; 
	// Number of volunteering places we visit
	private double placesToVisit; 
	// Volunteering or patrolling places actually visited so far
	private double placesCount = 0; 
	
	// File names of the specific location files
	private String homeLocationFile = null;
	private String mainPointLocationFile = null;
	private String osoccLocationsFile = null;
	private String baseCampLocationsFile = null;
	private String townHallLocationsFile = null;
	private String foodLocationFile = null; 
	private String burialsLocationFile = null; 
	private String airportLocationsFile = null;
	
	// Lists of coordinates for specific location files
	private List<Coord> homes = null; 
	private List<Coord> mainPoints = null; 
	private List<Coord> osocc = null;
	private List<Coord> baseCamp = null;
	private List<Coord> townHall = null;
	private List<Coord> foodWater = null; 
	private List<Coord> burials = null; 
	private List<Coord> airport = null;
	
	// Holds the nodes a chosen home location - Randomly chosen in order to hold account to invariances in the real world
	private Coord someHomeLocation;
	// Holds the nodes currently chosen point location - can be any place he want's to go to on the map
	private Coord somePointLocation;
	// Holds the nodes chosen OSOCC location
	private Coord someOsoccLocation;
	// Holds the nodes chosen town hall location
	private Coord someTownHallLocation;
	// Holds the nodes chosen base camp location - Randomly chosen in order to hold account to invariances in the real world
	private Coord someBaseCampLocation;
	// Holds the nodes currently chosen food location
	private Coord someFoodLocation; 
	// Holds the nodes chosen location of a burial site 
	private Coord someBurialLocation; 
	// Holds the nodes chosen airport location
	private Coord someAirportLocation;
	
	// The mode in which we are operating this activity right now
	private int mode; 
	// Saving the operational mode for actions after day 0 
	private int operationalMode = 0; 
	
	// Modes of operation
	// GO_TO_OSOCC -> Going to the OSOCC -> organizing disaster relief 
	private static final int GO_TO_OSOCC = 1; 
	// GO_TO_TOWN_HALL -> Going to the town hall -> Meeting of government and UN officials, on a regular basis
	// In this case "town hall" is just a representatively chosen word for any meeting point such as town or city hall or the police headquater (this always varies with the specific scenario conditions!) 
	private static final int GO_TO_TOWN_HALL = 2;
	// GO_TO_BASE_CAMP -> Going to the base camp -> meeting with disaster relief organizations
	private static final int GO_TO_BASE_CAMP = 3;
	// RECONNAISSANCE_MISSIONS -> Going on reconnaissance missions -> Exploring the situation in the post disaster area
	private static final int RECONNAISSANCE_MISSIONS = 4;
	// FOOD_WATER_DISTRIBUTION -> We go to food and water distribution locations (for helping with the distribution)
	private static final int FOOD_WATER_DISTRIBUTION = 5;
	// ORGANIZE_BURIAL -> Organize burials outsite the city center
	private static final int ORGANIZE_BURIAL = 6; 
	// GO_HOME -> Go home after being around the city all day
	private static final int GO_HOME = 7; 
	// IDLE_MODE -> Before switching to the next activity 
	private static final int IDLE_MODE = 8;
	
	// DijkstraPathFinder used for calculating our path 
	private DijkstraPathFinder pathFinder;

	// The exact timeslot when we (re-)started this activtiy 
	private double startedActivityTime;
	
	// LastLocation holds the last location a node visited (is initially set to some random home location after finishing the constructor, since many officials are spread around the area - will always be updated)
	private Coord lastLocation;
	// NextLocation holds the next location a node will visit (will always be updated)
	private Coord nextLocation;
	
	// To be set true if we're ready to start this activity --> Can be called from other classes via .Activate() function
	private boolean start = false;
	
	 // To be set true if we're done with this activity  ---> Status can be requested via .isReady() function
	private boolean ready = false; 
	
	// Random generator initalized with seed (if provided via settings file)
	private Random rand;
	
	// Local day counter 
	private int dayCounter; 
	
	// Current time we still need to wait
	private double waitingTime = 0; 
	
	/**
	 * Officials activity constructor
	 * @param settings
	 */
	public OfficialsActivityMovement(Settings settings) {
		super(settings);
		pathFinder = new DijkstraPathFinder(null);
		
		// Loading settings via default settings file
		if (settings.contains(RNGSEED)) {
			this.rngSeed = settings.getLong(RNGSEED);
		}
		else {
			System.out.println("You didn't specify a value as a seed for the random number generator!");
			System.out.println("rngSeed: " + this.rngSeed); 
		}
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
		if (settings.contains(PLACES_TO_VISIT)) {
			this.placesToVisit = settings.getDouble(PLACES_TO_VISIT);
		}
		else {
			System.out.println("You didn't specify a value for the amount of places we should visit!");
			System.out.println("placesToVisit: " + this.placesToVisit); 
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
			if (settings.contains(OSOCC_LOCATIONS_FILE_SETTING)) {
				this.osoccLocationsFile = settings.getSetting(OSOCC_LOCATIONS_FILE_SETTING);
			}
			else {
				System.out.println("You didn't specify a value for the OSOCC location file!");
				System.out.println("osoccLocationsFile: " + this.osoccLocationsFile); 
			}
			if (settings.contains(BASE_CAMP_LOCATIONS_FILE_SETTING)) {
				this.baseCampLocationsFile = settings.getSetting(BASE_CAMP_LOCATIONS_FILE_SETTING);
			}
			else {
				System.out.println("You didn't specify a value for the base camp location file!");
				System.out.println("baseCampLocationsFile: " + this.baseCampLocationsFile); 
			}
			if (settings.contains(TOWN_HALL_LOCATIONS_FILE_SETTING)) {
				this.townHallLocationsFile = settings.getSetting(TOWN_HALL_LOCATIONS_FILE_SETTING);
			}
			else {
				System.out.println("You didn't specify a value for the town hall location file!");
				System.out.println("townHallLocationsFile: " + this.townHallLocationsFile); 
			}
			if (settings.contains(FOOD_LOCATION_FILE_SETTINGS)) {
				this.foodLocationFile = settings.getSetting(FOOD_LOCATION_FILE_SETTINGS);
			}
			else {
				System.out.println("You didn't specify a value for the food and water distribution locations file!");
				System.out.println("foodLocationFile: " + this.foodLocationFile); 
			}
			if (settings.contains(BURIALS_LOCATION_FILE_SETTINGS)) {
				this.burialsLocationFile = settings.getSetting(BURIALS_LOCATION_FILE_SETTINGS);
			}
			else {
				System.out.println("You didn't specify a value for the burials location file!");
				System.out.println("burialsLocationFile: " + this.burialsLocationFile); 
			}
			if (settings.contains(AIRPORT_LOCATIONS_FILE_SETTING)) {
				this.airportLocationsFile = settings.getSetting(AIRPORT_LOCATIONS_FILE_SETTING);
			}
			else {
				System.out.println("You didn't specify a value for the airport location file!");
				System.out.println("airportLocationsFile: " + this.airportLocationsFile); 
			}
		} catch (Throwable t) {
			System.out.println("Reading the location files somehow failed - did you specify the correct path?"); 
		}
		
		// Create a new random generator for this activity with the provided seed -> Important for ensuring reproducable results! 
		this.rand = new Random(this.rngSeed);  
		
		// Set day counter to 0 since we start our simulation at day 0 
		this.dayCounter = 0;
		
		// Reading specific locations from the map files provided via default settings file
		SimMap map = getMap();
		Coord offset = map.getOffset();
		
		// Read home locations into local array
		this.homes = new LinkedList<Coord>();
		try {
		List<Coord> locationsRead1 = (new WKTReader()).readPoints(new File(homeLocationFile));
		for (Coord coord1 : locationsRead1) {
			// Mirroring all points if map data is mirrored
			if (map.isMirrored()) {
				coord1.setLocation(coord1.getX(), -coord1.getY());
			}
			coord1.translate(offset.getX(), offset.getY());
			this.homes.add(coord1);
		}
		// Chose a random position for a home location
		int firstRandom = this.getRandom(0,this.homes.size()-1);
		this.someHomeLocation = this.homes.get(firstRandom).clone();
		} catch (Throwable t) {
			System.out.println("Reading the home location file permanently failed");
			System.out.println("someHomeLocation " + this.someHomeLocation);
		}
		
		// Read main points locations into local array 
		this.mainPoints = new LinkedList<Coord>();
		try {
		List<Coord> locationsRead2 = (new WKTReader()).readPoints(new File(mainPointLocationFile));
		for (Coord coord2 : locationsRead2) {
			// Mirroring all points if map data is mirrored
			if (map.isMirrored()) {
				coord2.setLocation(coord2.getX(), -coord2.getY());
			}
			coord2.translate(offset.getX(), offset.getY());
			this.mainPoints.add(coord2);
		}
		// Chose a random position for the main points location 
		int secondRandom = this.getRandom(0,this.mainPoints.size()-1);
		this.somePointLocation = this.mainPoints.get(secondRandom).clone();
		} catch (Throwable t) {
			System.out.println("Reading the main points location file permanently failed");
			System.out.println("somePointLocation " + this.somePointLocation);
		}
		
		// Read OSOCC locations into local array 
		this.osocc = new LinkedList<Coord>();
		try {
		List<Coord> locationsRead3 = (new WKTReader()).readPoints(new File(osoccLocationsFile));
		for (Coord coord3 : locationsRead3) {
			// Mirroring all points if map data is mirrored
			if (map.isMirrored()) {
				coord3.setLocation(coord3.getX(), -coord3.getY());
			}
			coord3.translate(offset.getX(), offset.getY());
			this.osocc.add(coord3);
		}
		// Chose a random position for the OSOCC location 
		int thirdRandom = this.getRandom(0,this.osocc.size()-1);
		this.someOsoccLocation = this.osocc.get(thirdRandom).clone();
		} catch (Throwable t) {
			System.out.println("Reading the OSOCC location file permanently failed");
			System.out.println("someOsoccLocation " + this.someOsoccLocation);
		}
		
		// Read base camp locations into local array 
		this.baseCamp = new LinkedList<Coord>();
		try {
		List<Coord> locationsRead4 = (new WKTReader()).readPoints(new File(baseCampLocationsFile));
		for (Coord coord4 : locationsRead4) {
			// Mirroring all points if map data is mirrored
			if (map.isMirrored()) {
				coord4.setLocation(coord4.getX(), -coord4.getY());
			}
			coord4.translate(offset.getX(), offset.getY());
			this.baseCamp.add(coord4);
		}
		// Chose a random position for the base camp location 
		int fourthRandom = this.getRandom(0,this.baseCamp.size()-1);
		this.someBaseCampLocation = this.baseCamp.get(fourthRandom).clone();
		} catch (Throwable t) {
			System.out.println("Reading the base camp location file permanently failed");
			System.out.println("someBaseCampLocation " + this.someBaseCampLocation);
		}
		
		// Read town hall locations into local array 
		this.townHall = new LinkedList<Coord>();
		try {
		List<Coord> locationsRead5 = (new WKTReader()).readPoints(new File(townHallLocationsFile));
		for (Coord coord5 : locationsRead5) {
			// Mirroring all points if map data is mirrored
			if (map.isMirrored()) {
				coord5.setLocation(coord5.getX(), -coord5.getY());
			}
			coord5.translate(offset.getX(), offset.getY());
			this.townHall.add(coord5);
		}
		// Chose a random position for the town hall location 
		int fifthRandom = this.getRandom(0,this.townHall.size()-1);
		this.someTownHallLocation = this.townHall.get(fifthRandom).clone();
		} catch (Throwable t) {
			System.out.println("Reading the town hall location file permanently failed");
			System.out.println("someTownHallLocation " + this.someTownHallLocation);
		}
		
		// Read food and water distribution locations into local array 
		this.foodWater = new LinkedList<Coord>();
		try {
		List<Coord> locationsRead6 = (new WKTReader()).readPoints(new File(foodLocationFile));
		for (Coord coord6 : locationsRead6) {
			// Mirroring all points if map data is mirrored
			if (map.isMirrored()) {
				coord6.setLocation(coord6.getX(), -coord6.getY());
			}
			coord6.translate(offset.getX(), offset.getY());
			this.foodWater.add(coord6);
		}
		// Chose a random position for the food and water location 
		int sixthRandom = this.getRandom(0,this.foodWater.size()-1);
		this.someFoodLocation = this.foodWater.get(sixthRandom).clone();
		} catch (Throwable t) {
			System.out.println("Reading the food and water location file permanently failed");
			System.out.println("someFoodLocation " + this.someFoodLocation);
		}
		
		// Read burials locations into local array 
		this.burials = new LinkedList<Coord>();
		try {
		List<Coord> locationsRead7 = (new WKTReader()).readPoints(new File(burialsLocationFile));
		for (Coord coord7 : locationsRead7) {
			// Mirroring all points if map data is mirrored
			if (map.isMirrored()) {
				coord7.setLocation(coord7.getX(), -coord7.getY());
			}
			coord7.translate(offset.getX(), offset.getY());
			this.burials.add(coord7);
		}
		// Chose a random position for the burials location 
		int seventhRandom = this.getRandom(0,this.burials.size()-1);
		this.someBurialLocation = this.burials.get(seventhRandom).clone();
		} catch (Throwable t) {
			System.out.println("Reading the burials location file permanently failed");
			System.out.println("someBurialLocation " + this.someBurialLocation);
		}
		
		// Read airport locations into local array
		this.airport = new LinkedList<Coord>();
		try {
		List<Coord> locationsRead8 = (new WKTReader()).readPoints(new File(airportLocationsFile));
		for (Coord coord8 : locationsRead8) {
			// Mirroring all points if map data is mirrored
			if (map.isMirrored()) {
				coord8.setLocation(coord8.getX(), -coord8.getY());
			}
			coord8.translate(offset.getX(), offset.getY());
			this.airport.add(coord8);
		}
		// Chose a random position for a main street point location
		int eightRandom = this.getRandom(0,this.airport.size()-1);
		this.someAirportLocation = this.airport.get(eightRandom).clone();
		} catch (Throwable t) {
			System.out.println("Reading the airport location file permanently failed");
			System.out.println("somePointLocation " + this.someAirportLocation);
		}
		
		this.lastLocation = this.someHomeLocation;
		
		// Set initial mode
		this.mode = GO_TO_OSOCC;
		// Value of -1 means we haven't started yet
		this.startedActivityTime = -1;
	}

	/**
	 * Construct a new OfficialsActivityMovement instance from a prototype
	 * @param proto
	 */
	public OfficialsActivityMovement(OfficialsActivityMovement prototype) {
		super(prototype);
		this.pathFinder = prototype.pathFinder;
		
		// Loading settings via default settings file
		this.rand = prototype.getRand(); 
		this.dayLength = prototype.getDayLength(); 
		this.nbrOfDays = prototype.getNbrOfDays(); 
		this.placesToVisit = prototype.getPlacesToVisit(); 
		this.homes = prototype.getHomes(); 
		this.mainPoints = prototype.getMainPoints(); 
		this.osocc = prototype.getOsocc(); 
		this.baseCamp = prototype.getBaseCamp(); 
		this.townHall  = prototype.getTownHall(); 
		this.foodWater = prototype.getFoodWater(); 
		this.burials = prototype.getBurials(); 
		this.airport = prototype.getAirport(); 
		
		// Set day counter to 0 since we start our simulation at day 0 
		this.dayCounter = 0;
		
		// Chose a random position for the home location
		int firstRandom = this.getRandom(0,this.homes.size()-1);
		this.someHomeLocation = this.homes.get(firstRandom).clone();
		
		// Chose a random position for the main points location
		int secondRandom = this.getRandom(0,this.mainPoints.size()-1);
		this.somePointLocation = this.mainPoints.get(secondRandom).clone();
		
		// Chose a random position for the OSOCC location
		int thirdRandom = this.getRandom(0,this.osocc.size()-1);
		this.someOsoccLocation = this.osocc.get(thirdRandom).clone();
		
		// Chose a random position for the base camp location
		int fourthRandom = this.getRandom(0,this.baseCamp.size()-1);
		this.someBaseCampLocation = this.baseCamp.get(fourthRandom).clone();
		
		// Chose a random position for the town hall location 
		int fifthRandom = this.getRandom(0,this.townHall.size()-1);
		this.someTownHallLocation = this.townHall.get(fifthRandom).clone();
		
		// Chose a random position for the food and water location 
		int sixthRandom = this.getRandom(0,this.foodWater.size()-1);
		this.someFoodLocation = this.foodWater.get(sixthRandom).clone();
		
		// Chose a random position for the burials location 
		int seventhRandom = this.getRandom(0,this.burials.size()-1);
		this.someBurialLocation = this.burials.get(seventhRandom).clone();
		
		// Chose a random position for a main street point location
		int eightRandom = this.getRandom(0,this.airport.size()-1);
		this.someAirportLocation = this.airport.get(eightRandom).clone();
		
		this.lastLocation = this.someHomeLocation;
		
		// Set initial mode
		this.mode = GO_TO_OSOCC;
		// Value of -1 means we haven't started yet
		this.startedActivityTime = -1;
	}

	@Override
	public Path getPath() {
		// We only run into this if clause if a) the simulation has started b) our activity is activated right now c) the simulation hasn't reached it's end yet
		if ((SimClock.getTime() > 0) && (start) && (this.dayCounter != this.nbrOfDays)) {
		switch (mode) {
			case GO_TO_OSOCC: {
				// GO_TO_OSOCC -> Going to the OSOCC -> organizing disaster relief 
				if (this.startedActivityTime == -1) {
					// We just (re-)started this activity
					// Generating a home waiting time
					this.waitingTime = generateHomeWaitTime(); 
					// Saving the exact timeslot we started this activity 
					this.startedActivityTime = SimClock.getTime();
				}
				if (SimClock.getTime() >= (this.startedActivityTime + this.waitingTime)) {
					// Now we are sure that we waited long enough -> Going to OSOCC
					
					// Now we can calculate the PATH to OSOCC
					System.out.println("Calculating PATH to OSOCC");
					
					// Calculation of path to OSOCC
					SimMap map = super.getMap();
					if (map == null) {
						System.out.println("Error while getting map!");
							return null;
					}
					
					// Going to OSOCC
					this.nextLocation = someOsoccLocation.clone(); 
					
					// Creating the path 
					Path path = new Path(generateSpeed());
					try {
						// From actual location -> To OSOCC
						MapNode fromNode = map.getNodeByCoord(lastLocation); // Actual location
						MapNode toNode = map.getNodeByCoord(nextLocation); // OSOCC
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
					this.mode = GO_TO_TOWN_HALL; 
					
					// Calculating a waiting time to be sure that we don't arrive "too early" at the OSOCC location!
					this.waitingTime = generateHomeWaitTime();
					this.startedActivityTime = SimClock.getTime(); 
					System.out.println("Going to OSOCC!");
					System.out.println("Generated following PATH to OSOCC: " + path);
					return path;
				}
				else {
					// We still need to idle a bit
					System.out.println(" - GO_TO_OSOCC preparations mode - Active - Waiting"); 
					break; 
				}
			}
			case GO_TO_TOWN_HALL: {
				// GO_TO_TOWN_HALL -> Going to the town hall -> Meeting of government and UN officials, on a regular basis
				// In this case "town hall" is just a representatively chosen word for any meeting point such as town or city hall or the police headquater (this always varies with the specific scenario conditions!) 
				if (SimClock.getTime() >= (this.startedActivityTime + this.waitingTime)) {
					// Now we are sure that we waited long enough -> Go to town hall
					
					// Now we can calculate the PATH to town hall
					System.out.println("Calculating PATH to town hall");
					
					// Calculation of path to town hall
					SimMap map = super.getMap();
					if (map == null) {
						System.out.println("Error while getting map!");
							return null;
					}
					
					// Going to town hall
					this.nextLocation = someTownHallLocation.clone(); 
					
					// Creating the path 
					Path path = new Path(generateSpeed());
					try {
						// OSOCC -> To town hall
						MapNode fromNode = map.getNodeByCoord(lastLocation); // OSOCC
						MapNode toNode = map.getNodeByCoord(nextLocation); // Town hall
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
					this.mode = GO_TO_BASE_CAMP; 
					
					// Calculating a waiting time to be sure that we don't arrive "too early"
					this.waitingTime = generateHomeWaitTime();
					this.startedActivityTime = SimClock.getTime(); 
					System.out.println("Going to town hall!");
					System.out.println("Generated following PATH to town hall: " + path);
					return path;
				}
				else {
					// We still need to idle a bit
					System.out.println(" - GO_TO_OSOCC mode - Active - Waiting"); 
					break; 
				}
			}
			case GO_TO_BASE_CAMP: {
				// GO_TO_BASE_CAMP -> Going to the base camp -> meeting with disaster relief organizations
				if (SimClock.getTime() >= (this.startedActivityTime + this.waitingTime)) {
					// Now we are sure that we waited long enough -> Go to base camp
					
					// Now we can calculate the PATH to base camp
					System.out.println("Calculating PATH to base camp");
					
					// Calculation of path to base camp
					SimMap map = super.getMap();
					if (map == null) {
						System.out.println("Error while getting map!");
							return null;
					}
					
					// Going to base camp
					this.nextLocation = someBaseCampLocation.clone(); 
					
					// Creating the path 
					Path path = new Path(generateSpeed());
					try {
						// Town hall -> to base camp
						MapNode fromNode = map.getNodeByCoord(lastLocation); // Town hall
						MapNode toNode = map.getNodeByCoord(nextLocation); // Base camp
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
					
					// Ensuring we switch our activity as of the second day according to the parameters we chose for the second day
					if (this.operationalMode != 0 ) {
						this.mode = this.operationalMode; 
					}
					else {
						this.mode = RECONNAISSANCE_MISSIONS; 
					}
					
					// Calculating a waiting time to be sure that we don't arrive "too early"
					this.waitingTime = generateHomeWaitTime();
					this.startedActivityTime = SimClock.getTime(); 
					System.out.println("Going to base camp!");
					System.out.println("Generated following PATH to base camp: " + path);
					return path;
				}
				else {
					// We still need to idle a bit
					System.out.println(" - GO_TO_TOWN_HALL mode - Active - Waiting"); 
					break; 
				}
			}
			case RECONNAISSANCE_MISSIONS: {
				if (startedActivityTime == -1) {
					// We just (re-)started this activity
					// Generating a home waiting time
					this.waitingTime = generateHomeWaitTime(); 
					// Saving the exact timeslot we started this activity 
					this.startedActivityTime = SimClock.getTime();
				}
				// RECONNAISSANCE_MISSIONS -> Going on reconnaissance missions -> Exploring the situation in the post disaster area
				if (SimClock.getTime() >= (this.startedActivityTime + this.waitingTime)) {
					// We're done waiting, we can now go and explore the area
					if (this.placesToVisit != this.placesCount) {
						// Increasing the count of places we already visited while exploring the area
						this.placesCount++; 
						System.out.println("We are done waiting -> we now go on reconnaissance mission and explore the area");
	
						// Now we can calculate the PATH to go on reconnaissance mission
						System.out.println("Calculating PATH to go on reconnaissance mission");
						
						// Selecting a main point location for the reconnaissance mission
						int firstRandom = this.getRandom(0,this.mainPoints.size()-1);
						// Setting nextLocation to mainPoints location
						this.nextLocation = this.mainPoints.get(firstRandom).clone();
						
						// Calculation of path to go on reconnaissance mission
						SimMap map = super.getMap();
						if (map == null) {
							System.out.println("Error while getting map!");
							return null;
						}
						// Creating the path 
						Path path = new Path(generateSpeed());
						try {
							// From actual location -> To selected reconnaissance mission location
							MapNode fromNode = map.getNodeByCoord(lastLocation); // Actual location
							MapNode toNode = map.getNodeByCoord(nextLocation); // Reconnaissance mission location
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
						
						System.out.println("Generated following PATH to the reconnaissance mission location: " + path);
	
						return path;
					}
					else {
						// we visited enough places for exploring the area, now go back home 
						this.mode = GO_HOME;
						System.out.println("We visited enough places for exploring the area - Switching to GO_HOME mode"); 
						// Calculating a waiting time to be sure that we don't arrive "too early"
						this.startedActivityTime = SimClock.getTime();
						this.waitingTime = generateHomeWaitTime(); 
						break; 
					}
				}
				else {
					// We still need to idle since we haven't waited enough
					System.out.println(" - GO_TO_BASE_CAMP mode - Active - Waiting"); 
					break; 
				}
			}
			case GO_HOME: {
				// GO_HOME -> Go home after being around the city all day
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
						// From actual location -> To Home
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
					System.out.println(" - TO EARLY TO GO HOME - Stil active"); 
					break; 
				}
			}
			case IDLE_MODE: {
				// IDLE_MODE -> Before switching to the sleep activity 
				if ((SimClock.getTime() >= (this.startedActivityTime + this.waitingTime)) && (SimClock.getTime() > (this.dayLength*(this.dayCounter+1)))) {
					// We can continue if a) the actual SimClock time is greater than the old startedActivtyTime plus the current waiting time and
					// b) the actual SimClock time is greater than the (dayLength) times (the dayCounter +1), which means we successfully simulated one more day
					// we are done ideling -> now we can safely switch to sleep activity 
					
					// Checking weather to organize food and water distribution or burials the next day (50/50 chance)
					double tmp = this.getRandomDouble(); 
					if (tmp <= 0.3)
					{
						// We're going to organize food and water distribution the next day
						this.operationalMode = FOOD_WATER_DISTRIBUTION; 
					}
					if ((tmp > 0.3) && (tmp <= 0.6))
					{
						// We're going to organize burials the next day
						this.operationalMode = ORGANIZE_BURIAL; 
					}
					if (tmp > 0.6)
					{
						// We're going to conduct reconnaissance missions the next day
						this.operationalMode = RECONNAISSANCE_MISSIONS; 
					}
					
					// Reset places count to 0 so that we can restart the activity tomorrow 
					this.placesCount = 0; 
					// Reset mode such that we go to OSOCC the next morning 
					this.mode = GO_TO_OSOCC;
					
					// Reset parameteres in order to restart officials activity the next day
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
			}
			case FOOD_WATER_DISTRIBUTION: {
				// FOOD_WATER_DISTRIBUTION -> We go to food and water distribution locations (for helping with the distribution)
				if (startedActivityTime == -1) {
					// We just (re-)started this activity
					// Generating a home waiting time
					this.waitingTime = generateHomeWaitTime(); 
					// Saving the exact timeslot we started this activity 
					this.startedActivityTime = SimClock.getTime();
				}
				if (SimClock.getTime() >= (this.startedActivityTime + this.waitingTime)) {
					// We're done waiting, we can now go to distribute food and water
						System.out.println("We are done waiting -> we now go distribute food and water");
	
						// Now we can calculate the PATH to go distribute food and water
						System.out.println("Calculating PATH to go distribute food and water");
						
						// Selecting a location for the food and water distribution 
						int firstRandom = this.getRandom(0,this.foodWater.size()-1);
						// Setting nextLocation to a foodWater location
						this.nextLocation = this.foodWater.get(firstRandom).clone();
						
						// Calculation of path to go distribute food and water
						SimMap map = super.getMap();
						if (map == null) {
							System.out.println("Error while getting map!");
							return null;
						}
						// Creating the path 
						Path path = new Path(generateSpeed());
						try {
							// From actual location -> To food and water distribution location 
							MapNode fromNode = map.getNodeByCoord(lastLocation); // Actual location
							MapNode toNode = map.getNodeByCoord(nextLocation); //food and water location 
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
						this.waitingTime = generateLongWaitTime(); 
						
						System.out.println("Generated following PATH to the food and water distribution location: " + path);
						// Going home after the longer waiting time has passed later on
						this.mode = GO_HOME;
						
						return path; 
					}
				else {
					// We still need to idle since we haven't waited enough
					System.out.println(" - GO_TO_BASE_CAMP mode - Active - Waiting"); 
					break; 
				}
			}
			case ORGANIZE_BURIAL: {
				if (startedActivityTime == -1) {
					// We just (re-)started this activity
					// Generating a home waiting time
					this.waitingTime = generateHomeWaitTime(); 
					// Saving the exact timeslot we started this activity 
					this.startedActivityTime = SimClock.getTime();
				}
				// ORGANIZE_BURIAL -> Organize burials outsite the city center
				if (SimClock.getTime() >= (this.startedActivityTime + this.waitingTime)) {
					// We're done waiting, we can now go to go a burial location
					if (this.placesToVisit != this.placesCount) {
						// Increasing the count of burial places we already went to
						this.placesCount++;
						System.out.println("We are done waiting -> we now go to go a burial location");
	
						// Now we can calculate the PATH to go a burial location
						System.out.println("Calculating PATH to a burial location");
						
						// Selecting a burial location
						int firstRandom = this.getRandom(0,this.burials.size()-1);
						// Setting nextLocation to burials location
						this.nextLocation = this.burials.get(firstRandom).clone();
						
						// Calculation of path to a burial location
						SimMap map = super.getMap();
						if (map == null) {
							System.out.println("Error while getting map!");
							return null;
						}
						// Creating the path 
						Path path = new Path(generateSpeed());
						try {
							// From actual location -> To burial location
							MapNode fromNode = map.getNodeByCoord(lastLocation); // Actual location
							MapNode toNode = map.getNodeByCoord(nextLocation); //Burial location
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
						
						System.out.println("Generated following PATH to the burial location: " + path);
	
						return path;
					}
					else {
						// we've been to enough burial places, now go back home 
						this.mode = GO_HOME;
						System.out.println("We been to enough burial places - Switching to GO_HOME mode"); 
						// Calculating a waiting time to be sure that we don't arrive "too early"
						this.startedActivityTime = SimClock.getTime();
						this.waitingTime = generateHomeWaitTime(); 
						break; 
					}
				}
				else {
					// We still need to idle since we haven't waited enough
					System.out.println(" - GO_TO_BASE_CAMP mode - Active - Waiting"); 
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
		// The reason is that we don't need this function in the officials activity, yet we can make use of it to step trough our programm 
		// The value of 100 is arbitrary, yet values shouln't be too big or small in order that the ONE operates properly
		return 100;
	}
	
	protected double generateHomeWaitTime() {
		// We generate the waiting time we want to spent at home
		double tmpWaitTime = this.getRandomDouble()*8000;
		System.out.println("Generated a - generateHomeWaitTime() - OFFICIALS - AT HOME - waiting time of: " + tmpWaitTime); 
		return tmpWaitTime; 
	}
	
	protected double generateLongWaitTime() {
		// We generate a long waiting time 
		double tmpWaitTime = this.getRandomDouble()*20000;
		System.out.println("Generated a - generateLongWaitTime() - OFFICIALS - waiting time of: " + tmpWaitTime); 
		return tmpWaitTime; 
	}
	
	
	@Override
	public Coord getInitialLocation() {
		// We initially start being at home
		return someHomeLocation.clone();
	}
	
	public void setLocation(Coord c) {
		this.lastLocation = c.clone(); 
	}
	
	// Important since we need to know the last location of the node before switching to any other activity (otherwise we'll get null pointer exceptions!)
	public Coord getSomeUNLocation() {
		return this.someAirportLocation.clone();
	}
	
	// Important since we need to know the last location of the node before switching to any other activity (otherwise we'll get null pointer exceptions!)
	public Coord getSomeGOVLocation() {
		return this.someHomeLocation.clone();
	}

	// Method required for setting a correct initial location before the activity is launched -> Mandatory (if not you'll get null pointer exceptions!)
	public void setInitialLocation(Coord location) {
		this.lastLocation = location;
	}
	
	public List<Coord> getHomes()
	{
		return this.homes; 
	}
	
	public List<Coord> getMainPoints()
	{
		return this.mainPoints; 
	}
	
	public List<Coord> getOsocc()
	{
		return this.osocc; 
	}

	public List<Coord> getBaseCamp()
	{
		return this.baseCamp; 
	}
	
	public List<Coord> getTownHall()
	{
		return this.townHall; 
	}
	
	public List<Coord> getFoodWater()
	{
		return this.foodWater; 
	}
	
	public List<Coord> getBurials()
	{
		return this.burials; 
	}
	
	public List<Coord> getAirport()
	{
		return this.airport; 
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
	
	// Function for (re-) activating our officials mode
	// Function is to be called at the end of any other activity such that we can (re-) activate the officials mode
	public void Activate(int dayCounter) {
		this.dayCounter = dayCounter; 
		// True -> means we can start this activity at anytime as of now
		start = true;
		// False -> means we're not yet finished with this activity
		ready = false;
	}

	// Returns false if we haven't finished yet
	// Returns true if we are done with our officials activity so other activities know that we are ready to switch back such that they can proceed with their operations
	public boolean isReady() {
		return this.ready; 
	}
}
