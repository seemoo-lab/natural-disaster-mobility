/*
 * Copyright 2015 Tom Schons - TU Darmstadt, Germany
 * Released under GPLv3. See LICENSE.txt for details.
 */
package movement;

import java.util.List;
import java.util.Arrays;
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
 * The NonInjuredPopulationActivityMovement class represents the movement of non-injured people in a disaster zone
 *
 * Activity simulates daily activities of non-injured people in a disaster;
 * Some people are actively walking in the surroundings of their homes, trying to find family members etc. 
 * Some try to find food and water distribution locations
 * Other help disaster relief organizations with cleanup operations 
 * Whilst others are patrolling in the streets to ensure public order
 * All non-injured people go home to sleep in the evening 
 *
 * @author Tom Schons
 */
public class NonInjuredPopulationActivityMovement extends MapBasedMovement implements SwitchableMovement {

	// Constants for importing settings from default settings file
	public static final String DAY_LENGTH = "dayLength";
	public static final String NUMBER_OF_DAYS = "nbrOfDays";
	public static final String SLEEPING_TIME_MIN = "sleepingTimeMin";
	// Seed used for initializing the scenario's random number generator -> The same seed will always produce the same output which is mandatory for creating reproducable results!
	public static final String RNGSEED = "rngSeed";
	// Number of neighbors in near surroundings to be visited
	public static final String NEIGHBORS_TO_VISIT = "neighborsToVisit"; 
	// Number of places to be visited
	public static final String PLACES_TO_VISIT = "placesToVisit"; 
	
	// Probability loaded via external default settings file (if provided)
	// Probability that we volunteer for helping the incoming disaster relief organizations with cleaning the streets
	// NOTE: If we don't do this, we volunteer for moving around the city and ensuring safety in groups
	public static final String PROBABILITY_TO_VOLUNTEER_FOR_DISASTER_RELIEF = "reliefVolunteeringProb";

	// Location files loaded via external default settings file (if provided)
	public static final String HOME_LOCATION_FILE_SETTING = "homeLocationFile";
	public static final String MAIN_POINT_LOCATION_FILE_SETTING = "mainPointLocationFile";
	public static final String FOOD_LOCATION_FILE_SETTINGS = "foodLocationFile";
	
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
	
	// Number of neighbors in near surroundings to visit 
	private double neighborsToVisit; 
	// Number of volunteering places we visit
	private double placesToVisit; 
	// Volunteering or patrolling places actually visited so far
	private double placesCount = 0; 
	// Neighbors actually visited so far
	private double visitedNeighborsCount = 0; 
	
	// File names of the specfic location files
	private String homeLocationFile = null;
	private String mainPointLocationFile = null;
	private String foodLocationFile = null; 
	
	// Lists of coordinates for specific location files
	private List<Coord> homes = null; 
	private List<Coord> mainPoints = null; 
	private List<Coord> foodWater = null; 
	
	// Holds the nodes permanently chosen home location - Randomly chosen in order to hold account to invariances in the real world
	private Coord someHomeLocation;
	// Holds the nodes currently chosen point location - can be any place he want's to go to on the map
	private Coord somePointLocation;
	// Holds the nodes currently chosen food location
	private Coord someFoodLocation; 
	
	// The mode in which we are operating this activity right now
	private int mode; 
	
	// Modes of operation
	// ACTIVE_AROUND_HOME -> Right after the disaster occurred we stay at home, we try to find family members and neighbors in the immediate surroundings
	private static final int ACTIVE_AROUND_HOME = 0;
	// ACTIVE_AROUND_HOME_SURROUNDINGS -> Later on we assess the damage around home and try to help more neighbors in near surroundings
	private static final int ACTIVE_AROUND_HOME_SURROUNDINGS = 1;
	// MOVE_AROUND_FIND_FOOD_WATER -> We start to move around to find food and water (or such distribution centers)
	private static final int MOVE_AROUND_FIND_FOOD_WATER = 2;
	// VOLUNTEER_FOR_CLEANING -> Some part of the population usually volunteers for cleaning operations -> they are slowly moving around
	private static final int VOLUNTEER_FOR_CLEANING = 3;
	// MARCHING_IN_STREETS -> Some non-injured are patrolling in the streets while others try to resume daily life
	private static final int MARCHING_IN_STREETS = 4;
	// GO_HOME -> Nodes go home after being in the city all day
	private static final int GO_HOME = 5;
	// IDLE_MODE -> Used when we returned home but it's "too early" to switch back to sleep mode 
	private static final int IDLE_MODE = 6;
	
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
	
	// Current time we still need to wait
	private double waitingTime = 0; 
	
	// Wheather or not we've already visited some neighbors
	private boolean visitedNeighbor = false;
	// Wheather or not we've already been at a food place for today
	private boolean beenToFoodPlace = false; 
	
	// Calculates with certain probability (if provided via settings file) wheather or not we'll help incoming disaster relief organizations 
	private boolean reliefVolunteering = false; 
	
	/**
	 * NonInjuredPopulationActivityMovement constructor
	 * @param settings
	 */
	public NonInjuredPopulationActivityMovement(Settings settings) {
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
		if (settings.contains(NEIGHBORS_TO_VISIT)) {
			this.neighborsToVisit = settings.getDouble(NEIGHBORS_TO_VISIT);
		}
		else {
			System.out.println("You didn't specify a value for the amount of neighbors we should visit!");
			System.out.println("neighborsToVisit: " + this.neighborsToVisit); 
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
			if (settings.contains(FOOD_LOCATION_FILE_SETTINGS)) {
				this.foodLocationFile = settings.getSetting(FOOD_LOCATION_FILE_SETTINGS);
			}
			else {
				System.out.println("You didn't specify a value for the food location file!");
				System.out.println("foodLocationFile: " + this.foodLocationFile); 
			}
		} catch (Throwable t) {
			System.out.println("Reading the location files somehow failed - did you specify the correct path?"); 
		}
		
		// Create a new random generator for this activity with the provided seed -> Important for ensuring reproducable results! 
		this.rand = new Random(this.rngSeed);  
		
		// Set day counter to 0 since we start our simulation at day 0 
		this.dayCounter = 0;
		
		// Set reliefVolunteering variable once and for all for the entire simulation period, based on reliefVolunteeringProb provided via default settings file
		if (((this.getRandomDouble() * this.reliefVolunteeringProb) * 2) <= 0.5) {
			// We'll volunteer to help disaster relief organizations with their work for the rest of this simulation
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
		
		// Read food and water distribution locations into local array 
		this.foodWater = new LinkedList<Coord>();
		try {
		List<Coord> locationsRead3 = (new WKTReader()).readPoints(new File(foodLocationFile));
		for (Coord coord3 : locationsRead3) {
			// Mirroring all points if map data is mirrored
			if (map.isMirrored()) {
				coord3.setLocation(coord3.getX(), -coord3.getY());
			}
			coord3.translate(offset.getX(), offset.getY());
			this.foodWater.add(coord3);
		}
		// Chose a random position for the food and water location 
		int thirdRandom = this.getRandom(0,this.foodWater.size()-1);
		this.someFoodLocation = this.foodWater.get(thirdRandom).clone();
		} catch (Throwable t) {
			System.out.println("Reading the food and water location file permanently failed");
			System.out.println("someFoodLocation " + this.someFoodLocation);
		}
		
		// Set fixed home location of the non-injured node
		this.lastLocation = this.someHomeLocation.clone(); 
		
		// Set initial mode
		this.mode = ACTIVE_AROUND_HOME;
		// Value of -1 means we haven't started yet
		this.startedActivityTime = -1;
	}

	/**
	 * Construct a new NonInjuredPopulationActivityMovement instance from a prototype
	 * @param proto
	 */
	public NonInjuredPopulationActivityMovement(NonInjuredPopulationActivityMovement prototype) {
		super(prototype);
		this.pathFinder = prototype.pathFinder;
		
		// Loading settings via default settings file
		this.rand = prototype.getRand(); 
		this.dayLength = prototype.getDayLength(); 
		this.nbrOfDays = prototype.getNbrOfDays(); 
		this.neighborsToVisit = prototype.getNeighborsToVisit(); 
		this.placesToVisit = prototype.getPlacesToVisit(); 
		this.sleepingTimeMin = prototype.getSleepingTimeMin(); 
		this.mainPoints = prototype.getMainPoints(); 
		this.homes = prototype.getHomes(); 
		this.foodWater = prototype.getFoodWater(); 

		// Chose a random position for the homes location
		int firstRandom = this.getRandom(0,this.homes.size()-1);
		this.someHomeLocation = this.homes.get(firstRandom).clone();

		// Chose a random position for the main points location
		int secondRandom = this.getRandom(0,this.mainPoints.size()-1);
		this.somePointLocation = this.mainPoints.get(secondRandom).clone();

		// Chose a random position for the food and water location
		int thirdRandom = this.getRandom(0,this.foodWater.size()-1);
		this.someFoodLocation = this.foodWater.get(thirdRandom).clone();
		
		// Set day counter to 0 since we start our simulation at day 0 
		this.dayCounter = 0;
		
		// Set fixed home location of the non-injured node
		this.lastLocation = this.someHomeLocation.clone(); 
		
		// Set initial mode
		this.mode = ACTIVE_AROUND_HOME; 
		// Value of -1 means we haven't started yet
		this.startedActivityTime = -1;
	}

	@Override
	public Path getPath() {
		// We only run into this if clause if a) the simulation has started b) our activity is activated right now c) the simulation hasn't reached it's end yet
		if ((SimClock.getTime() > 0) && (start) && (this.dayCounter != this.nbrOfDays)) {
		switch (mode) {
			case ACTIVE_AROUND_HOME: {
				// ACTIVE_AROUND_HOME -> Right after the disaster occurred we stay at home, we try to find family members and neighbors in the immediate surroundings
				if (startedActivityTime == -1) {
					// We just (re-)started this activity
					// Generating a home waiting time
					this.waitingTime = generateHomeWaitTime(); 
					// Saving the exact timeslot we started this activity 
					this.startedActivityTime = SimClock.getTime();
				}
				if (SimClock.getTime() >= (this.startedActivityTime + this.waitingTime)) {
					// We're done waiting, we can now go visit our next door neighbor
					System.out.println("We are done waiting -> now visiting our next door neighbor");
					// Go visit our neighbor
					if (!visitedNeighbor) {
						// Set visited neighbor to true
						this.visitedNeighbor = true;

						// Now we can calculate the PATH to go to the neighbor
						System.out.println("Calculating PATH to go to the neighbor");
					
						// Calculation of path to the neighbor
						SimMap map = super.getMap();
						if (map == null) {
							System.out.println("Error while getting map!");
							return null;
						}
						
						// Creating the path 
						Path path = new Path(generateSpeed());
						try {
							// From Home -> To Neighbor
							MapNode fromNode = map.getNodeByCoord(someHomeLocation); // Home
							
							// Finding a neighbor next door to go to
							List<MapNode> neighbors = new ArrayList<MapNode>();
							// Get list of neighbors
							neighbors = fromNode.getNeighbors();
							if (neighbors.size() > 0) {
								 // Get next door neighbor location
								MapNode nextNeighbor = neighbors.get(0);
								// Set toNode location to neighbor location
								MapNode toNode = map.getNodeByCoord(nextNeighbor.getLocation().clone());
								this.nextLocation = nextNeighbor.getLocation().clone(); // Neighbor
								List<MapNode> nodePath = pathFinder.getShortestPath(fromNode, toNode);

								for (MapNode node : nodePath) {
									path.addWaypoint(node.getLocation());
									}
							}
							else {
								System.out.println("Unfortunately we have no direct neighbor");
								break; 
							}
						}
						catch (Throwable t)
						{
							System.out.println("Error while creating the path!");
							t.printStackTrace();
						}
						
						// Ensuring that the last location is always updated after we finish the path creation method
						this.lastLocation = nextLocation.clone();
						
						// Calculating a waiting time to be sure that we don't arrive "too early" at our neighbors location!
						this.startedActivityTime = SimClock.getTime();
						this.waitingTime = generateHomeWaitTime(); 
						
						System.out.println("Generated following PATH from HOME to the neighbor: " + path);
						
						return path;
					}
					
					// After visiting the neighbor we go to next mode
					this.mode = ACTIVE_AROUND_HOME_SURROUNDINGS; 
					break; 
				}
				else {
					// We still need to idle since we haven't waited enough
					System.out.println(" - ACTIVE_AROUND_HOME mode - Active - Waiting"); 
					break; 
				}
			}
			case ACTIVE_AROUND_HOME_SURROUNDINGS: {
				// ACTIVE_AROUND_HOME_SURROUNDINGS -> Later on we assess the damage around home and try to help more neighbors in near surroundings
				if (SimClock.getTime() >= (this.startedActivityTime + this.waitingTime)) {
					// We waited long enough, we are now going to visit more neighbors 
					if (this.neighborsToVisit != this.visitedNeighborsCount) {
						// Increasing the count of neighbors we already visited
						this.visitedNeighborsCount++; 
						// Now we can calculate the PATH to go to the neighbor
						System.out.println("Calculating PATH to go to another neighbor");
					
						// Calculation of path to the neighbor
						SimMap map = super.getMap();
						if (map == null) {
							System.out.println("Error while getting map!");
							return null;
						}
						
						// Creating the path 
						Path path = new Path(generateSpeed());
						try {
							// From Neighbor -> To Neighbor 
							MapNode fromNode = map.getNodeByCoord(lastLocation); // Neighbor
							
							// Finding another neighbor we can go to
							List<MapNode> neighbors = new ArrayList<MapNode>();
							// Get list of neighbors
							neighbors = fromNode.getNeighbors();
							if (neighbors.size() > 0) {
								// Get a random neighbor 
								int firstRandom = this.getRandom(0,neighbors.size()-1);
								 // Get some neighbor location
								MapNode nextNeighbor = neighbors.get(firstRandom);
								// Set toNode location to neighbor location
								MapNode toNode = map.getNodeByCoord(nextNeighbor.getLocation().clone());
								this.nextLocation = nextNeighbor.getLocation().clone(); // Neighbor
								List<MapNode> nodePath = pathFinder.getShortestPath(fromNode, toNode);

								for (MapNode node : nodePath) {
									path.addWaypoint(node.getLocation());
									}
							}
							else {
								System.out.println("Unfortunately we have no direct neighbor");
								break; 
							}
						}
						catch (Throwable t)
						{
							System.out.println("Error while creating the path!");
							t.printStackTrace();
						}
						
						// Ensuring that the last location is always updated after we finish the path creation method
						this.lastLocation = nextLocation.clone();
						
						// Calculating a waiting time to be sure that we don't arrive "too early" at our neighbors location!
						this.startedActivityTime = SimClock.getTime();
						this.waitingTime = generateHomeWaitTime(); 
						
						System.out.println("Generated following PATH from neighbor to neighbor: " + path);
						System.out.println("Visiting neighbor number: " + this.visitedNeighborsCount);
						
						return path;
					}
					else {
						// we visited enough neighbors, now switching to next mode
						this.mode = MOVE_AROUND_FIND_FOOD_WATER;
						System.out.println("We visited enough neighbors - Switching to MOVE_AROUND_FIND_FOOD_WATER mode"); 
						// Calculating a waiting time to be sure that we don't arrive "too early"
						this.startedActivityTime = SimClock.getTime();
						this.waitingTime = generateHomeWaitTime(); 
						break; 
					}
				}
				else {
					// We still need to idle at the neighbors place
					System.out.println(" - ACTIVE_AROUND_HOME_SURROUNDINGS mode - Active - Waiting"); 
					break; 
				}
			}
			case MOVE_AROUND_FIND_FOOD_WATER: {
				// MOVE_AROUND_FIND_FOOD_WATER -> We start to move around to find food and water (or such distribution centers)
				if (SimClock.getTime() >= (this.startedActivityTime + this.waitingTime)) {
					// We waited long enough, we are now going to visit more neighbors 
					if (!beenToFoodPlace) {
						// Set beenToFoodPlace to true
						this.beenToFoodPlace = true; 
						// Now we can calculate the PATH to go to the next food and water distribution place
						System.out.println("Calculating PATH to go a food and water distribution place");					

						this.nextLocation = someFoodLocation.clone(); // Food location
						
						// Calculation of path to the food location
						SimMap map = super.getMap();
						if (map == null) {
							System.out.println("Error while getting map!");
							return null;
						}
						
						// Creating the path 
						Path path = new Path(generateSpeed());
						try {
							// From Neighbor -> To Food location 
							MapNode fromNode = map.getNodeByCoord(lastLocation); // On first run neighbor location, as of seond run food location
							MapNode toNode = map.getNodeByCoord(nextLocation); // Food location
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
						
						// Calculating a waiting time to be sure that we don't arrive "too early" at the food and water location!
						this.startedActivityTime = SimClock.getTime();
						this.waitingTime = generateHomeWaitTime(); 
						
						System.out.println("Generated following PATH to the food location: " + path);
						
						return path;
					}
					else {
						// We've been to a food place now
						if (reliefVolunteering) {
							// Go to VOLUNTEER_FOR_CLEANING mode
							this.mode = VOLUNTEER_FOR_CLEANING;
							System.out.println("We've been to a food place - Switching to VOLUNTEER_FOR_CLEANING mode");
						}
						else {
							// Go to MARCHING_IN_STREETS mode
							this.mode = MARCHING_IN_STREETS;
							System.out.println("We've been to a food place - Switching to MARCHING_IN_STREETS mode");
						}
						// Calculating a waiting time to be sure that we don't arrive "too early"
						this.startedActivityTime = SimClock.getTime();
						this.waitingTime = generateHomeWaitTime();
						break; 
					}
				}
				else {
					// We still need to idle when being at a food distribution place
					System.out.println(" - MOVE_AROUND_FIND_FOOD_WATER mode - Active - Waiting"); 
					break; 
				}
			}
			case VOLUNTEER_FOR_CLEANING: {
				// VOLUNTEER_FOR_CLEANING -> Some part of the population usually volunteers for cleaning operations -> they are slowly moving around
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
					System.out.println(" - VOLUNTEER_FOR_CLEANING mode - Active - Waiting"); 
					break; 
				}
			}
			case MARCHING_IN_STREETS: {
				// MARCHING_IN_STREETS -> Some non-injured are patrolling in the streets while others try to resume daily life
				if (SimClock.getTime() >= (this.startedActivityTime + this.waitingTime)) {
					// We waited long enough, we are now going to walk around the streets helping our neighborhood while patrolling in the streets
					
					// Now we can calculate the PATH to go marching through the streets
					System.out.println("Calculating PATH to go marching through the streets");

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
						System.out.println("Distance to closest neighbor calculated to be: " + closestDistance);
						System.out.println("Distance to furthest neighbor calculated to be: " + furthestDistance);
						
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
					
					System.out.println("Generated following PATH when going patrolling: " + path);
					return path;
				}
				else {
					// We still need to idle while patrolling in the streets
					System.out.println(" - MARCHING_IN_STREETS mode - Active - Waiting"); 
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
					
					// Reset parameteres in order to restart non-injured activity next day
					this.ready = true; 
					this.start = false; 
					// Set beenToFoodPlace to false such that we are able to go back to the food place tomorrow as well
					this.beenToFoodPlace = false;
					// Reset places count to 0 so that we can restart the activity tomorrow 
					this.placesCount = 0; 
					// Reset mode such that activity can restart tomorrow 
					this.mode = MOVE_AROUND_FIND_FOOD_WATER; 
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
		// The reason is that we don't need this function in the non-injured activity, yet we can make use of it to step trough our programm 
		// The value of 100 is arbitrary, yet values shouln't be too big or small in order that the ONE operates properly
		return 100;
	}
	
	protected double generateHomeWaitTime() {
		// We generate the waiting time we want to spent at home
		double tmpWaitTime = this.getRandomDouble()*2000;
		System.out.println("Generated a - generateHomeWaitTime() - NON-INJURED POPULATION - AT HOME - waiting time of: " + tmpWaitTime); 
		return tmpWaitTime; 
	}
	
	protected double generateVolunteeringWaitTime() {
		// We generate the waiting time we want to spent volunteering
		double tmpWaitTime = this.getRandomDouble()*5000;
		System.out.println("Generated a - generateVolunteeringWaitTime() - NON-INJURED POPULATION - waiting time of: " + tmpWaitTime); 
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
	
	public Coord getHomeLocation() {
		return someHomeLocation.clone();
	}
	
	public Coord getLocation() {
		return lastLocation.clone();
	}

	public List<Coord> getMainPoints()
	{
		return this.mainPoints; 
	}
	
	public List<Coord> getHomes()
	{
		return this.homes; 
	}
	
	public List<Coord> getFoodWater()
	{
		return this.foodWater; 
	}
	
	public long getSeed() {
		return this.rngSeed; 
	}
	
	public double getNeighborsToVisit() {
		return this.neighborsToVisit; 
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
	
	// Function for (re-) activating our non-injured mode
	// Function is to be called at the end of any other activity such that we can (re-) activate the non-injured mode
	public void Activate(int dayCounter) {
		this.dayCounter = dayCounter; 
		// True -> means we can start this activity at anytime as of now
		start = true;
		// False -> means we're not yet finished with this activity
		ready = false;
	}

	// Returns false if we haven't finished yet
	// Returns true if we are done with our non-injured activity so other activities know that we are ready to switch back such that they can proceed with their operations
	public boolean isReady() {
		return this.ready; 
	}
}
