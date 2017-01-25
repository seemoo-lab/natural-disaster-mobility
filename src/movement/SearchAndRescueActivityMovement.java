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
 * The SearchAndRescueActivityMovement class represents the movement of search and rescue teams in a disaster zone
 * 
 * Search and rescue teams usually arrive in the disaster zone via the airport (handled in separate activity)
 * Then they go to the OSOCC for briefings with governemt an UN officials, before starting their search and rescue activities
 * Since there are many different strategies and patterns for conducting search and rescue missions, we'll focus on one that is implemented here
 * (Yet, the model can easily be extended to include further methods and patterns).
 * Search and rescue units also go back home in the evening such that they can sleep at their base camp
 *
 * @author Tom Schons
 */
public class SearchAndRescueActivityMovement extends MapBasedMovement  implements SwitchableMovement {

	// Constants for importing settings from default settings file
	public static final String DAY_LENGTH = "dayLength";
	public static final String NUMBER_OF_DAYS = "nbrOfDays";
	// Number of places to be visited
	public static final String PLACES_TO_VISIT = "placesToVisit"; 
	
	// Location files loaded via external default settings file (if provided)
	public static final String MAIN_POINT_LOCATION_FILE_SETTING = "mainPointLocationFile";
	public static final String OSOCC_LOCATIONS_FILE_SETTING = "osoccLocationsFile";
	public static final String BASE_CAMP_LOCATIONS_FILE_SETTING = "baseCampLocationsFile";
	public static final String AIRPORT_LOCATIONS_FILE_SETTING = "airportLocationsFile";
	
	// Length of the day in seconds
	private double dayLength; 
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
	private String airportLocationsFile = null;
	
	// Lists of coordinates for specific location files
	private List<Coord> mainPoints = null; 
	private List<Coord> osocc = null;
	private List<Coord> baseCamp = null;
	private List<Coord> airport = null;
	
	// Holds the nodes currently chosen point location - can be any place he want's to go to on the map
	private Coord somePointLocation;
	// Holds the nodes chosen OSOCC location
	private Coord someOsoccLocation;
	// Holds the nodes chosen base camp location - Randomly chosen in order to hold account to invariances in the real world
	private Coord someBaseCampLocation;
	// Holds the nodes chosen airport location
	private Coord someAirportLocation;
	
	// The mode in which we are operating this activity right now
	private int mode; 
	
	// Modes of operation
	// GO_TO_OSOCC -> Going to the OSOCC -> for daily briefings and coordination
	private static final int GO_TO_OSOCC = 1; 
	// SEARCH_AND_RESCUE -> Search and rescue activity
	private static final int SEARCH_AND_RESCUE = 2; 
	// GO_HOME -> Go home after being around the city all day
	private static final int GO_HOME = 3; 
	// IDLE_MODE -> Before switching to the next activity 
	private static final int IDLE_MODE = 4;
	
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
	
	// To be set true if we want to go to the airport and leave the country 
	private boolean goToAirport = false; 

	// Local day counter 
	private int dayCounter; 
	
	// Current time we still need to wait
	private double waitingTime = 0; 
	
	/**
	 * SearchAndRescueActivityMovement constructor
	 * @param settings
	 */
	public SearchAndRescueActivityMovement(Settings settings) {
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
	 * Construct a new SearchAndRescueActivityMovement instance from a prototype
	 * @param prototype
	 */
	public SearchAndRescueActivityMovement(SearchAndRescueActivityMovement prototype) {
		super(prototype);
		this.pathFinder = prototype.pathFinder;
		
		// Loading settings via default settings file
		this.dayLength = prototype.getDayLength();
		this.nbrOfDays = prototype.getNbrOfDays(); 
		this.placesToVisit = prototype.getPlacesToVisit(); 
		this.mainPoints = prototype.getMainPoints(); 
		this.osocc = prototype.getOsocc(); 
		this.baseCamp = prototype.getBaseCamp(); 
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
			// GO_TO_OSOCC -> Going to the OSOCC -> organizing search and rescue missions
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
				this.mode = SEARCH_AND_RESCUE; 
				
				// Calculating a waiting time to be sure that we don't arrive "too early" at the OSOCC location!
				this.waitingTime = generateLongWaitTime();
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
		case SEARCH_AND_RESCUE: {
			// SEARCH_AND_RESCUE -> Search and rescue activity  
			if (SimClock.getTime() >= (this.startedActivityTime + this.waitingTime)) {
				// We waited long enough, we are now going to our search and rescue mission

				// Now we can calculate the PATH to our search and rescue mission
				System.out.println("Calculating PATH to our search and rescue mission");

				// Calculation of path to search and rescue mission location 
				SimMap map = super.getMap();
				if (map == null) {
					System.out.println("Error while getting map!");
						return null;
				}
				
				// The node we want to go to
				MapNode newToNode = null; 

				// Calculating next location we're going to
				if (this.placesCount == 0) {
					// We first (re-)started the search and rescue mission, so we chose a random location on the map as a starting point
					int firstRandom = this.getRandom(0,this.mainPoints.size()-1);
					this.nextLocation = this.mainPoints.get(firstRandom).clone();
					// Map location to node
					newToNode = map.getNodeByCoord(this.nextLocation);
					this.waitingTime = generateHomeWaitTime();
					this.placesCount++;
				}
				else if (this.placesCount <= this.placesToVisit) {
					// We can still continue with our search and rescue mission, visiting the next house on this street
					this.placesCount++;
					this.waitingTime = generateHomeWaitTime();
				}
				else if (this.placesCount > this.placesToVisit) {
					// After visiting one last place we jump to the next mode and we're done with the search and rescue mission for today
					this.mode = GO_HOME;
					this.placesCount++;
					System.out.println("Going back home soon, after visiting this one last place");
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
					
					// Go to the geographically closest node (e.g. go to the next house in the street)
					double closestDistance = Integer.MAX_VALUE;
					double secondClosestDistance = 0;
					double thisDistance = 0;
					MapNode nextNeighbor = null;
					MapNode secondNextNeighbor = null;
					for (int j = 0; j < neighbors.size()-1; j++) {
						thisDistance = this.nextLocation.distance(neighbors.get(j).getLocation()); 
						if (thisDistance < closestDistance) {
							// We found the geographically closest neighbor
							closestDistance = thisDistance;
							nextNeighbor = neighbors.get(j);
						}
						if (closestDistance > secondClosestDistance) {
							// We found the geographically second-closest neighbor
							secondClosestDistance = thisDistance;
							secondNextNeighbor = neighbors.get(j);
						}
					}
					System.out.println("Distance to closest neighor calculated to be: " + closestDistance);
					
					if (nextNeighbor == null) {
						// Avoid the very rare case that we have so few neighbors that we would run in a null pointer exception otherwise
						nextNeighbor = neighbors.get(0);
					}
					
					if (secondNextNeighbor == null) {
						// Avoid the very rare case that we have so few neighbors that we would run in a null pointer exception otherwise
						secondNextNeighbor = neighbors.get(0);
					}
					
					// Set toNode location to neighbor location -> awoid going backwards by not always taking the closest neighbor
					if (this.placesCount % 2 == 0) {
						// Go to next door neighbor 
						try {
							newToNode = map.getNodeByCoord(nextNeighbor.getLocation().clone());
							this.nextLocation = nextNeighbor.getLocation().clone(); // Next neighbor
						}
						catch (Throwable t)
						{
							System.out.println("No such neighbor available!");
							t.printStackTrace();
						}
					}
					else {
						// Go to second next door neighbor 
						try {
							newToNode = map.getNodeByCoord(secondNextNeighbor.getLocation().clone());
							this.nextLocation = secondNextNeighbor.getLocation().clone(); // Second next neighbor
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
					// To search and rescue mission location
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
				// Saving the actual activity time
				this.startedActivityTime = SimClock.getTime();
				
				System.out.println("Generated following PATH when going on a search and rescue mission: " + path);
				return path;
			}
			else {
				// We still need to idle while in search and rescue mode
				System.out.println(" - SEARCH_AND_RESCUE mode - Active - Waiting"); 
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
				
				// Reset places count to 0 so that we can restart the activity tomorrow 
				this.placesCount = 0; 
				// Reset mode such that we go to OSOCC the next morning 
				this.mode = GO_TO_OSOCC;
				
				if (this.nbrOfDays == this.dayCounter + 2) {
					// We're leaving the disaster area via the airport as of tomorrow
					// We switch to the "go to airport" activity and then leave the disater area 
					// Ensuring that all scientists leave on the last day of the disaster
					this.placesCount = this.placesToVisit;
					this.goToAirport = true;
					System.out.println("Going back to the airport and then heading home!");
				}
				
				// Reset parameteres in order to restart search and rescue activity the next day
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
	  }
	 }
	return null; 
	}
	
	@Override
	protected double generateWaitTime() {
		// Since our movement model is more or less fixed by the parameters loaded from the external default settings file we don't really need this function
		// Especially since generateWaitTime() is called by internal functions of the ONE after each getPath() method has returned, so we just use this here as a simple step counter
		// The reason is that we don't need this function in the search and rescue activity, yet we can make use of it to step trough our programm 
		// The value of 100 is arbitrary, yet values shouln't be too big or small in order that the ONE operates properly
		return 100;
	}
	
	protected double generateHomeWaitTime() {
		// We generate the waiting time we want to spent at home
		double tmpWaitTime = this.getRandomDouble()*6000;
		System.out.println("Generated a - generateHomeWaitTime() - SEARCH AND RESCUE - waiting time of: " + tmpWaitTime); 
		return tmpWaitTime; 
	}
	
	protected double generateLongWaitTime() {
		// We generate a long waiting time 
		double tmpWaitTime = this.getRandomDouble()*12000;
		System.out.println("Generated a - generateLongWaitTime() - SEARCH AND RESCUE - waiting time of: " + tmpWaitTime); 
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
	
	public double getDayLength() {
		return this.dayLength; 
	}
	
	public int getNbrOfDays() {
		return this.nbrOfDays; 
	}
	
	public void setInitialLocation(Coord c) {
		this.lastLocation = c.clone(); 
	}
	
	public boolean getGoToAirport() {
		return this.goToAirport; 
	}
	
	// Function for (re-) activating our search and rescue activity
	// Function is to be called at the end of any other activity such that we can (re-) activate the search and rescue activity
	public void Activate(int dayCounter) {
		this.dayCounter = dayCounter; 
		// True -> means we can start this activity at anytime as of now
		start = true;
		// False -> means we're not yet finished with this activity
		ready = false;
	}

	// Returns false if we haven't finished yet
	// Returns true if we are done with our search and rescue activity so other activities know that we are ready to switch back such that they can proceed with their operations
	public boolean isReady() {
		return this.ready; 
	}
}
