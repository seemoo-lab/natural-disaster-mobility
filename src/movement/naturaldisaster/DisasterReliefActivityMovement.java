/*
 * Copyright 2015 Tom Schons - TU Darmstadt, Germany
 * Released under GPLv3. See LICENSE.txt for details.
 */
package movement.naturaldisaster;

import java.util.List;

import movement.MapBasedMovement;
import movement.Path;
import movement.SwitchableMovement;
import movement.map.DijkstraPathFinder;
import movement.map.MapNode;
import core.Coord;
import core.Settings;

import input.WKTReader;
import java.io.File;
import java.util.LinkedList;
import java.util.ArrayList;
import movement.map.SimMap;
import core.SimClock;

/**
 * The DisasterReliefActivityMovement class represents the movement submodel of disaster relief units in a disaster area
 * 
 * Disaster relief units usually arrive via the airport (not included here, but in the "arrival at airport" activitiy)
 * As of the first day disaster relief organizations meet on a daily basis with UN and government officials (at the city hall or OSOCC)
 * Disaster relief organizations distribute food and water to the population and help to free roads from debris  
 * In the evening to go back home to their sleeping spot (usually the base camp)
 *
 * @author Tom Schons
 */
public class DisasterReliefActivityMovement extends MapBasedMovement implements SwitchableMovement {
	
	// Constants for importing settings from default settings file
	public static final String NUMBER_OF_DAYS = "nbrOfDays";
	// Number of places to be visited
	public static final String PLACES_TO_VISIT = "placesToVisit"; 
	
	// Location files loaded via external default settings file (if provided)
	public static final String MAIN_POINT_LOCATION_FILE_SETTING = "mainPointLocationFile";
	public static final String OSOCC_LOCATIONS_FILE_SETTING = "osoccLocationsFile";
	public static final String BASE_CAMP_LOCATIONS_FILE_SETTING = "baseCampLocationsFile";
	public static final String TOWN_HALL_LOCATIONS_FILE_SETTING = "townHallLocationsFile";
	public static final String FOOD_LOCATION_FILE_SETTINGS = "foodLocationFile";
	public static final String AIRPORT_LOCATIONS_FILE_SETTING = "airportLocationsFile";

	/** Length of the day in seconds */
	private static final int SECONDS_IN_A_DAY = 24 * 60 * 60;
	// Number of days
	private int nbrOfDays; 
	// Number of places we visit
	private double placesToVisit; 
	// Places actually visited so far
	private double placesCount = 0;
	
	// File names of the specific location files
	private String mainPointLocationFile = null;
	private String osoccLocationsFile = null;
	private String baseCampLocationsFile = null;
	private String townHallLocationsFile = null;
	private String foodLocationFile = null; 
	private String airportLocationsFile = null;
	
	// Lists of coordinates for specific location files
	private List<Coord> mainPoints = null; 
	private List<Coord> osocc = null;
	private List<Coord> baseCamp = null;
	private List<Coord> townHall = null;
	private List<Coord> foodWater = null; 
	private List<Coord> airport = null;
	
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
	// Holds the nodes chosen airport location
	private Coord someAirportLocation;
	
	// The mode in which we are operating this activity right now
	private int mode; 
	
	// Modes of operation
	// GO_TO_OSOCC -> Going to the OSOCC -> organizing disaster relief 
	private static final int GO_TO_OSOCC = 1; 
	// GO_TO_TOWN_HALL -> Going to the town hall -> Meeting of government and UN officials, on a regular basis
	// In this case "town hall" is just a representatively chosen word for any meeting point such as town or city hall or the police headquater (this always varies with the specific scenario conditions!) 
	private static final int GO_TO_TOWN_HALL = 2;
	// FOOD_WATER_DISTRIBUTION -> We go to food and water distribution locations (for helping with the distribution)
	private static final int FOOD_WATER_DISTRIBUTION = 3;
	// MARCHING_IN_STREETS -> Some disaster relief organizations are marching through the streets, helping locals where they can (providing food, clearing rubble etc.)
	private static final int MARCHING_IN_STREETS = 4; 
	// GO_HOME -> Go home after being around the city all day
	private static final int GO_HOME = 5; 
	// IDLE_MODE -> Before switching to the next activity 
	private static final int IDLE_MODE = 6;
	
	// DijkstraPathFinder used for calculating our path 
	private DijkstraPathFinder pathFinder;

	// The exact timeslot when we (re-)started this activtiy 
	private double startedActivityTime;
	
	// LastLocation holds the last location a node visited (will always be updated)
	private Coord lastLocation;
	// NextLocation holds the next location a node will visit (will always be updated)
	private Coord nextLocation;
	
	// To be set true if we're ready to start this activity --> Can be called from other classes via .Activate() function
	private boolean start = false;
	
	 // To be set true if we're done with this activity  ---> Status can be requested via .isReady() function
	private boolean ready = false; 
	
	// Local day counter
	private int dayCounter; 
	
	// Current time we still need to wait
	private double waitingTime = 0; 
	
	/**
	 * DisasterReliefActivityMovement constructor
	 * @param settings
	 */
	public DisasterReliefActivityMovement(Settings settings) {
		super(settings);
		pathFinder = new DijkstraPathFinder(null);
		
		// Loading settings via default settings file
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
		
		// Set day counter to 0 since we start our simulation at day 0
		this.dayCounter = 0;
		
		// Reading specific locations from the map files provided via default settings file
		SimMap map = getMap();
		Coord offset = map.getOffset();
		
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
		
		// Home location 
		this.lastLocation = this.someAirportLocation;
		
		// Set initial mode
		this.mode = GO_TO_OSOCC;
		// Value of -1 means we haven't started yet
		this.startedActivityTime = -1;
	}

	/**
	 * Construct a new DisasterReliefActivityMovement instance from a prototype
	 * @param prototype
	 */
	public DisasterReliefActivityMovement(DisasterReliefActivityMovement prototype) {
		super(prototype);
		this.pathFinder = prototype.pathFinder;
		
		// Loading settings via default settings file
		this.nbrOfDays = prototype.getNbrOfDays();
		this.placesToVisit = prototype.getPlacesToVisit(); 
		this.mainPoints = prototype.getMainPoints(); 
		this.osocc = prototype.getOsocc(); 
		this.baseCamp = prototype.getBaseCamp(); 
		this.townHall  = prototype.getTownHall(); 
		this.foodWater = prototype.getFoodWater(); 
		this.airport = prototype.getAirport(); 
		
		// Set day counter to 0 since we start our simulation at day 0 
		this.dayCounter = 0;
		
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
		
		// Chose a random position for a main street point location
		int eightRandom = this.getRandom(0,this.airport.size()-1);
		this.someAirportLocation = this.airport.get(eightRandom).clone();
		
		// Home location
		this.lastLocation = this.someAirportLocation;
		
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
					return path;
				}
				else {
					// We still need to idle a bit
					break;
				}
			}
			case GO_TO_TOWN_HALL: {
				// GO_TO_TOWN_HALL -> Going to the town hall -> Meeting of government and UN officials, on a regular basis
				// In this case "town hall" is just a representatively chosen word for any meeting point such as town or city hall or the police headquater (this always varies with the specific scenario conditions!) 
				if (SimClock.getTime() >= (this.startedActivityTime + this.waitingTime)) {
					// Now we are sure that we waited long enough -> Go to town hall
					
					// Now we can calculate the PATH to town hall

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
					
					// Ensuring we switch our activity as of the second day according to the parameters we chose for the second day
					if (this.getRandomDouble() < 0.5) {
						this.mode = FOOD_WATER_DISTRIBUTION ; 
					}
					else {
						this.mode = MARCHING_IN_STREETS; 
					}
					
					// Calculating a waiting time to be sure that we don't arrive "too early"
					this.waitingTime = generateHomeWaitTime();
					this.startedActivityTime = SimClock.getTime(); 
					return path;
				}
				else {
					// We still need to idle a bit
					break;
				}
			}
			case FOOD_WATER_DISTRIBUTION: {
				// FOOD_WATER_DISTRIBUTION -> We go to food and water distribution locations (for helping with the distribution)
				if (SimClock.getTime() >= (this.startedActivityTime + this.waitingTime)) {
					// We're done waiting, we can now go to distribute food and water

						// Now we can calculate the PATH to go distribute food and water

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
						
						// Going home after the longer waiting time has passed later on
						this.mode = GO_HOME;
						
						return path; 
					}
				else {
					// We still need to idle since we haven't waited enough
					break;
				}
			}
			case MARCHING_IN_STREETS: {
				// MARCHING_IN_STREETS -> Some disaster relief organizations are marching through the streets, helping locals where they can (providing food, clearing rubble etc.)
				if (SimClock.getTime() >= (this.startedActivityTime + this.waitingTime)) {
					// We waited long enough, we are now going to walk around the streets helping our neighborhood while marching through the city's streets
					
					// Now we can calculate the PATH to go marching through the streets

					// Calculation of path for marching through the streets
					SimMap map = super.getMap();
					if (map == null) {
						System.out.println("Error while getting map!");
							return null;
					}
					
					// The node we want to go to
					MapNode newToNode = null; 
					
					// Calculating next location we're going to
					if (this.placesCount == 0) {
						// We first (re-)started the marching through the streets sub-activity, so we chose a random location on the map as a starting point
						int firstRandom = this.getRandom(0,this.mainPoints.size()-1);
						this.nextLocation = this.mainPoints.get(firstRandom).clone();
						// Map location toNode
						newToNode = map.getNodeByCoord(this.nextLocation);
						this.waitingTime = generateHomeWaitTime();
						this.placesCount++;
					}
					else if (this.placesCount <= this.placesToVisit) {
						// We can still continue with our sub-activity, visiting the next house on this street
						this.placesCount++;
						this.waitingTime = generateHomeWaitTime();
					}
					else if (this.placesCount > this.placesToVisit) {
						// After visiting one last place we jump to the next mode and we're done for today -> now go back home
						this.mode = GO_HOME;
						this.placesCount++;
						this.waitingTime = generateHomeWaitTime();
						this.startedActivityTime = SimClock.getTime(); 
					}
					
					// Important to know -> Even if "mainPoints" is an ArrayList, simply taking the next element won't work in our case since the ArrayList
					// is not geographically sorted and thus the next element (after our position in the list) is generally not geographically close to our position!
					
					if (this.placesCount != 0) {
						// Obtaining the list of node neighbors, thus nodes geographically close to our location
						List<MapNode> neighbors = new ArrayList<MapNode>();
						try {
							// catching the very rare case that this.nextLocation would have no valid coords & avoid a null pointer via this try-catch block
							neighbors = map.getNodeByCoord(this.nextLocation).getNeighbors();						
						}
						catch (Throwable t)
						{
							System.out.println("No such location - chosing a new, but random, location!");
							// Setting a new random neighbor to avoid null pointer
							int firstRandom = this.getRandom(0,this.mainPoints.size()-1);
							this.nextLocation = this.mainPoints.get(firstRandom).clone();
							neighbors = map.getNodeByCoord(this.nextLocation).getNeighbors();	
						}
						while (neighbors.size() == 0) {
							// We have no neighbors anymore -> chose new random position 
							int firstRandom = this.getRandom(0,this.mainPoints.size()-1);
							neighbors = map.getNodeByCoord(this.nextLocation).getNeighbors();
						}
					
						// Chose a location to walk to 
						double closestDistance = Integer.MAX_VALUE;
						double furthestDistance = 0;
						double thisDistance = 0;
						MapNode closestNeighbor = null;
						MapNode furthestNeighbor = null;
						for (int j = 0; j < neighbors.size(); j++) {
							thisDistance = this.nextLocation.distance(neighbors.get(j).getLocation()); 
							if (thisDistance < closestDistance) {
								// We found the geographically closest neighbor
								closestDistance = thisDistance;
								closestNeighbor = neighbors.get(j);
							}
							if (thisDistance > furthestDistance) {
								// We found the geographically furthest neighbor
								furthestDistance = thisDistance;
								furthestNeighbor = neighbors.get(j);
							}
						}

						if (closestNeighbor == null) {
							// Avoid the very rare case that we have so few neighbors that we would run in a null pointer exception otherwise
							closestNeighbor = neighbors.get(0);
						}
						
						if (furthestNeighbor == null) {
							// Avoid the very rare case that we have so few neighbors that we would run in a null pointer exception otherwise
							furthestNeighbor = neighbors.get(0);
						}
						
						if (this.placesCount % 2 == 0) {
							// Go to closest neighbor 
							try {
								newToNode = map.getNodeByCoord(closestNeighbor.getLocation().clone());
								this.nextLocation = closestNeighbor.getLocation().clone();
							}
							catch (Throwable t)
							{
								System.out.println("No such neighbor available!");
								t.printStackTrace();
							}
						}
						else {
							// Go to furthest neighbor 
							try {
								newToNode = map.getNodeByCoord(furthestNeighbor.getLocation().clone());
								this.nextLocation = furthestNeighbor.getLocation().clone();
							}
							catch (Throwable t)
							{
								System.out.println("No such neighbor available!");
								t.printStackTrace();
							}
						}
					}
					
					// Creating the path 
					Path path = new Path(generateSpeed());
					try {
						// From -> To location as calculated above
						MapNode fromNode = map.getNodeByCoord(lastLocation); // Actual location
						// Use calculated toNode from above
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
					// Saving the actual activity time
					this.startedActivityTime = SimClock.getTime();
					
					return path;
				}
				else {
					// We still need to idle while marching through the streets
					break;
				}
			}
			case GO_HOME: {
				// GO_HOME -> Go home after being around the city all day
				if (SimClock.getTime() >= (this.startedActivityTime + this.waitingTime)) {
					// Now we are sure that we waited long enough -> Go home and then sleep there 
					
					// Now we can calculate the PATH to go back home	

					// Calculation of path back home
					SimMap map = super.getMap();
					if (map == null) {
						System.out.println("Error while getting map!");
							return null;
					}
					
					// Going back to the home location
					this.nextLocation = someBaseCampLocation.clone(); 
					
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
					return path;
				}
				else {
					// We still need to idle a bit
					break;
				}
			}
			case IDLE_MODE: {
				// IDLE_MODE -> Before switching to the sleep activity 
				if ((SimClock.getTime() >= (this.startedActivityTime + this.waitingTime)) && (SimClock.getTime() > (this.SECONDS_IN_A_DAY *(this.dayCounter+1)))) {
					// We can continue if a) the actual SimClock time is greater than the old startedActivtyTime plus the current waiting time and
					// b) the actual SimClock time is greater than the (SECONDS_IN_A_DAY) times (the dayCounter +1), which means we successfully simulated one more day
					// we are done ideling -> now we can safely switch to sleep activity 
					
					// Reset places count to 0 so that we can restart the activity tomorrow 
					this.placesCount = 0; 
					// Reset mode such that we go to OSOCC the next morning 
					this.mode = GO_TO_OSOCC;
					
					// Reset parameteres in order to restart disaster relief activity the next day
					this.ready = true; 
					this.start = false; 

					this.startedActivityTime = -1; 
				}
				else {
					// We still need to idle as it's to early to go to sleep again
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
		// The reason is that we don't need this function in the disaster relief organization activity, yet we can make use of it to step trough our programm 
		// The value of 100 is arbitrary, yet values shouln't be too big or small in order that the ONE operates properly
		return 100;
	}
	
	protected double generateHomeWaitTime() {
		// We generate the waiting time we want to spent at home
		double tmpWaitTime = this.getRandomDouble()*8000;
		return tmpWaitTime;
	}
	
	protected double generateLongWaitTime() {
		// We generate a long waiting time 
		double tmpWaitTime = this.getRandomDouble()*20000;
		return tmpWaitTime;
	}
		
	@Override
	public Coord getInitialLocation() {
		// We initially start being at home
		return someBaseCampLocation.clone();
	}
	
	public Coord getLastLocation() {
		return lastLocation.clone();
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
	
	public List<Coord> getAirport()
	{
		return this.airport; 
	}

	public double getPlacesToVisit() {
		return this.placesToVisit; 
	}
	
	// Get random int value, between provided min and max; returns 0 if invalid argument is provided 
	private int getRandom(int min, int max) {
		if ((min >= 0) && (max > 0)) {
			return rng.nextInt(max - min) + min;
		}
		return 0; 
	}

	// Get random double value, between 0.0 and 1.0
	private double getRandomDouble() {
		return rng.nextDouble();
	}

	public int getNbrOfDays() {
		return this.nbrOfDays; 
	}
	
	public void setInitialLocation(Coord c) {
		this.lastLocation = c.clone(); 
	}

	// Function for (re-) activating our disaster relief mode
	// Function is to be called at the end of any other activity such that we can (re-) activate the disaster relief mode
	public void Activate(int dayCounter) {
		this.dayCounter = dayCounter; 
		// True -> means we can start this activity at anytime as of now
		start = true;
		// False -> means we're not yet finished with this activity
		ready = false;
	}

	// Returns false if we haven't finished yet
	// Returns true if we are done with our disaster relief activity so other activities know that we are ready to switch back such that they can proceed with their operations
	public boolean isReady() {
		return this.ready; 
	}
}
