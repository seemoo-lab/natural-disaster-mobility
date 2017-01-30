/*
 * Copyright 2015 Tom Schons - TU Darmstadt, Germany
 * Released under GPLv3. See LICENSE.txt for details.
 */
package movement;

import java.util.*;

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
 * The ArrivalActivityMovement class represents the arrival of individuals in a disaster area
 * 
 * As the arrival in a disaster area is often defined by an arrival at an airport, we first simulate an arrival at the airport. 
 * Then usually follows a registration at the RDC, after that a walk/drive to the OSOCC.
 * Afterwards the base camp location is rejoined for creating a sleeping accommodation for the upcoming nights
 *
 * @author Tom Schons
 */
public class ArrivalActivityMovement extends MapBasedMovement implements SwitchableMovement {

	// Constants for importing settings from default settings file
	public static final String OFFSET_START_DELAY = "offsetStartDelay";
	
	// Location files loaded via external default settings file (if provided)
	public static final String AIRPORT_LOCATIONS_FILE_SETTING = "airportLocationsFile";
	public static final String RDC_LOCATIONS_FILE_SETTING = "rdcLocationsFile";
	public static final String OSOCC_LOCATIONS_FILE_SETTING = "osoccLocationsFile";
	public static final String BASE_CAMP_LOCATIONS_FILE_SETTING = "baseCampLocationsFile";
	
	// Offset that is added to delay the start of this particular activity
	private double offsetStartDelay;
	
	// File names of the specific location files
	private String airportLocationsFile = null;
	private String rdcLocationsFile = null;
	private String osoccLocationsFile = null;
	private String baseCampLocationsFile = null;
	
	// Lists of coordinates for specific location files
	private List<Coord> airport = null;
	private List<Coord> rdc = null;
	private List<Coord> osocc = null;
	private List<Coord> baseCamp = null;
	
	// Holds the nodes chosen airport location - Randomly chosen in order to hold account to invariances in the real world
	private Coord someAirportLocation;
	// Holds the nodes chosen RDC location - Randomly chosen in order to hold account to invariances in the real world
	private Coord someRdcLocation;
	// Holds the nodes chosen OSOCC location - Randomly chosen in order to hold account to invariances in the real world
	private Coord someOsoccLocation; 
	// Holds the nodes chosen base camp location - Randomly chosen in order to hold account to invariances in the real world
	private Coord someBaseCampLocation;
	
	// The mode in which we are operating this activity right now
	private int mode; 
	
	// Modes of operation
	// ARRIVAL_MODE -> Right after arriving at the airport in (or close to) the disaster area
	private static final int ARRIVAL_MODE = 0;
	// GO_TO_RDC -> After arriving at airport go to the RDC
	private static final int GO_TO_RDC = 1;
	// GO_TO_OSOCC -> After arriving at the RDC go to the OSOCC
	private static final int GO_TO_OSOCC = 2;
	// GO_TO_BASE_CAMP -> After arriving at the OSOCC go to the base camp
	private static final int GO_TO_BASE_CAMP = 3;
	// IDLE_MODE -> After arriving at the base camp idle while switching to the next activity 
	private static final int IDLE_MODE = 4;
	
	// DijkstraPathFinder used for calculating our path 
	private DijkstraPathFinder pathFinder;

	// The exact timeslot when we (re-)started this activtiy 
	private double startedActivityTime;
	
	// LastLocation holds the last location a node visited (is initially set to some airport location after finishing the constructor, see below - will always be updated)
	private Coord lastLocation;
	// NextLocation holds the next location a node will visit (will always be updated)
	private Coord nextLocation;  
	
	// To be set true if we're ready to start this activity --> Can be called from other classes via .Activate() function
	// Since we first have to arrive via the airport we set this true here
	private boolean start = true;
	
	 // To be set true if we're done with this activity  ---> Status can be requested via .isReady() function
	private boolean ready = false; 
	
	// Current time we still need to wait
	private double waitingTime = 0; 
	
	/**
	 * Arrival activity constructor
	 * @param settings
	 */
	public ArrivalActivityMovement(Settings settings) {
		super(settings);
		pathFinder = new DijkstraPathFinder(null);
		
		// Loading settings via default settings file
		if (settings.contains(OFFSET_START_DELAY)) {
			this.offsetStartDelay = settings.getDouble(OFFSET_START_DELAY);
		}
		else {
			System.out.println("You didn't specify a value for the offset start delay!");
			System.out.println("offsetStartDelay: " + this.offsetStartDelay); 
		}
		
		// Loading location files as specified via default settings file (has to be done via try-catch)
		try {	
			if (settings.contains(AIRPORT_LOCATIONS_FILE_SETTING)) {
				this.airportLocationsFile = settings.getSetting(AIRPORT_LOCATIONS_FILE_SETTING);
			}
			else {
				System.out.println("You didn't specify a value for the airport location file!");
				System.out.println("airportLocationsFile: " + this.airportLocationsFile); 
			}
			if (settings.contains(RDC_LOCATIONS_FILE_SETTING)) {
				this.rdcLocationsFile = settings.getSetting(RDC_LOCATIONS_FILE_SETTING);
			}
			else {
				System.out.println("You didn't specify a value for the RDC location file!");
				System.out.println("rdcLocationsFile: " + this.rdcLocationsFile); 
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
		} catch (Throwable t) {
			System.out.println("Reading the location files somehow failed - did you specify the correct path?"); 
		}
		
		// Reading specific locations from the map files provided via default settings file
		SimMap map = getMap();
		Coord offset = map.getOffset();
		
		// Read airport locations into local array
		this.airport = new LinkedList<Coord>();
		try {
		List<Coord> locationsRead1 = (new WKTReader()).readPoints(new File(airportLocationsFile));
		for (Coord coord1 : locationsRead1) {
			// Mirroring all points if map data is mirrored
			if (map.isMirrored()) {
				coord1.setLocation(coord1.getX(), -coord1.getY());
			}
			coord1.translate(offset.getX(), offset.getY());
			this.airport.add(coord1);
		}
		// Chose a random position for an airport location
		int firstRandom = this.getRandom(0,this.airport.size()-1);
		this.someAirportLocation = this.airport.get(firstRandom).clone();
		} catch (Throwable t) {
			System.out.println("Reading the airport location file permanently failed");
			System.out.println("someAirportLocation " + this.someAirportLocation);
		}
		
		// Read RDC locations into local array 
		this.rdc = new LinkedList<Coord>();
		try {
		List<Coord> locationsRead2 = (new WKTReader()).readPoints(new File(rdcLocationsFile));
		for (Coord coord2 : locationsRead2) {
			// Mirroring all points if map data is mirrored
			if (map.isMirrored()) {
				coord2.setLocation(coord2.getX(), -coord2.getY());
			}
			coord2.translate(offset.getX(), offset.getY());
			this.rdc.add(coord2);
		}
		// Chose a random position for the RDC location 
		int secondRandom = this.getRandom(0,this.rdc.size()-1);
		this.someRdcLocation = this.rdc.get(secondRandom).clone();
		} catch (Throwable t) {
			System.out.println("Reading the RDC location file permanently failed");
			System.out.println("someRdcLocation " + this.someRdcLocation);
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
		
		// Set last location of the arrival activity to an airport location
		this.lastLocation = this.someAirportLocation.clone(); 
		
		// Set initial mode
		this.mode = ARRIVAL_MODE;
		// Value of -1 means we haven't started yet
		this.startedActivityTime = -1;
	}

	/**
	 * Construct a new ArrivalActivityMovement instance from a prototype
	 * @param prototype
	 */
	public ArrivalActivityMovement(ArrivalActivityMovement prototype) {
		super(prototype);
		this.pathFinder = prototype.pathFinder;
		
		// Loading settings via default settings file
		this.airport = prototype.getAirport();
		this.rdc = prototype.getRdc(); 
		this.osocc = prototype.getOsocc(); 
		this.baseCamp = prototype.getBaseCamp(); 
		
		// Ensuring not all nodes arrive at the very same time at the airport 
		int tmp = (int) this.offsetStartDelay / 2; 
		this.offsetStartDelay += this.getRandom(0,tmp);
		
		// Chose a random position for the airport location
		int firstRandom = this.getRandom(0,this.airport.size()-1);
		this.someAirportLocation = this.airport.get(firstRandom).clone();

		// Chose a random position for the RDC location
		int secondRandom = this.getRandom(0,this.rdc.size()-1);
		this.someRdcLocation = this.rdc.get(secondRandom).clone();
		
		// Chose a random position for the OSOCC location
		int thirdRandom = this.getRandom(0,this.osocc.size()-1);
		this.someOsoccLocation = this.osocc.get(thirdRandom).clone();
		
		// Chose a random position for the base camp location
		int fourthRandom = this.getRandom(0,this.baseCamp.size()-1);
		this.someBaseCampLocation = this.baseCamp.get(fourthRandom).clone();
		
		// Set fixed home location of the scientific node
		this.lastLocation = this.someAirportLocation.clone(); 
		
		// Set initial mode
		this.mode = ARRIVAL_MODE; 
		// Value of -1 means we haven't started yet
		this.startedActivityTime = -1;
	}

	@Override
	public Path getPath() {
		// We only run into this if clause if a) the simulation has started b) our activity is activated right now
		if ((SimClock.getTime() > 0) && start) {
		switch (mode) {
			case ARRIVAL_MODE: {
				// ARRIVAL_MODE -> Right after arriving at the airport in (or close to) the disaster area
				if (startedActivityTime == -1) {
					// We just started this activity
					// Generating a home waiting time since in our simulation it's still night and to remain realistic we have to assume people arrived at the airport during the day
					this.waitingTime = generateHomeWaitTime() * 2; 
					
					// Adding potential offset (provided via default settings file) for the first arrival activity
					this.waitingTime += this.offsetStartDelay;
					
					// Saving the exact timeslot we started this activity 
					this.startedActivityTime = SimClock.getTime();
				}
				if (SimClock.getTime() >= (this.startedActivityTime + this.waitingTime)) {
					// We're done waiting, we can now go to the RDC 
					// Calculating a little waiting time to be sure that we don't arrive "too early"
						this.startedActivityTime = SimClock.getTime();
						this.waitingTime = generateHomeWaitTime() / 2; 
						this.mode = GO_TO_RDC;
						break;
				}
				else {
					// We still need to idle since we haven't waited enough
					break;
				}
			}
			case GO_TO_RDC: {
			// GO_TO_RDC -> After arriving at airport go to the RDC
				if (SimClock.getTime() >= (this.startedActivityTime + this.waitingTime)) {
					// Now we are sure that we waited long enough -> Go to the RDC now
					
					// Now we can calculate the PATH to the RDC

					// Calculation of path to the RDC
					SimMap map = super.getMap();
					if (map == null) {
						System.out.println("Error while getting map!");
							return null;
					}
					
					// Going to the RDC
					this.nextLocation = someRdcLocation.clone(); 
					
					// Creating the path 
					Path path = new Path(generateSpeed());
					try {
						// From airport -> To RDC
						MapNode fromNode = map.getNodeByCoord(lastLocation); // Airport
						MapNode toNode = map.getNodeByCoord(nextLocation); // RDC
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
					this.mode = GO_TO_OSOCC; 
					
					// Calculating a longer waiting time to be sure that we don't head "too early" to the OSOCC
					this.waitingTime = generateHomeWaitTime() * 10;
					this.startedActivityTime = SimClock.getTime(); 
					return path;
				}
				else {
					// We still need to idle a bit
					break;
				}
			} 
			case GO_TO_OSOCC: {
				// GO_TO_OSOCC -> After arriving at the RDC go to the OSOCC
				if (SimClock.getTime() >= (this.startedActivityTime + this.waitingTime)) {
					// Now we are sure that we waited long enough -> Go to OSOCC 
					
					// Now we can calculate the PATH to go to OSOCC 

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
						// From RDC -> To OSOCC
						MapNode fromNode = map.getNodeByCoord(lastLocation); // RDC
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
					this.mode = GO_TO_BASE_CAMP; 
					
					// Calculating a longer waiting time to be sure that we don't head "too early" to the base camp
					this.waitingTime = generateHomeWaitTime() * 10;
					this.startedActivityTime = SimClock.getTime(); 
					return path;
				}
				else {
					// We still need to idle a bit
					break;
				}
			} 
			case GO_TO_BASE_CAMP: {
				// GO_TO_BASE_CAMP -> After arriving at the OSOCC go to the base camp
				if (SimClock.getTime() >= (this.startedActivityTime + this.waitingTime)) {
					// Now we are sure that we waited long enough -> Go to base camp
					
					// Now we can calculate the PATH to base camp

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
						// From OSOCC -> To base camp
						MapNode fromNode = map.getNodeByCoord(lastLocation); // OSOCC
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
					this.mode = IDLE_MODE; 
					
					// Calculating a longer waiting time to be sure that we don't switch "too early" to the idle mode
					this.waitingTime = generateHomeWaitTime() * 10;
					this.startedActivityTime = SimClock.getTime(); 
					return path;
				}
				else {
					// We still need to idle a bit
					break;
				}
			}
			case IDLE_MODE: {
				// IDLE_MODE -> After arriving at the base camp idle while switching to the next activity 
				if (SimClock.getTime() >= (this.startedActivityTime + this.waitingTime)) {
					// Now we are sure that we waited long enough -> Switch to another activity
					ready = true;
					start = false; 
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
		return tmpWaitTime;
	}
	
	@Override
	public Coord getInitialLocation() {
		// We initially start at the airport
		return someAirportLocation.clone();
	}
	
	public void setLocation(Coord location) {
		this.lastLocation = location; 
	}
	
	// Important since we need to know the last location of the node before switching to any other activity (otherwise we'll get null pointer exceptions!)
	public Coord getLastLocation() {
		return lastLocation.clone();
	}
	
	public Coord getSomeAirportLocation() {
		return someAirportLocation.clone();
	}
	
	public List<Coord> getAirport()
	{
		return this.airport; 
	}
	
	public List<Coord> getRdc()
	{
		return this.rdc; 
	}
	
	public List<Coord> getOsocc()
	{
		return this.osocc; 
	}
	
	public List<Coord> getBaseCamp()
	{
		return this.baseCamp; 
	}
	
	// Get random int value, between provided min and max; returns 0 if invalid argument is provided 
	public int getRandom(int min, int max) {
		if ((min >= 0) && (max > 0)) {
			return rng.nextInt(max - min) + min;
		}
		return 0; 
	}

	// Get random double value, between 0.0 and 1.0
	private double getRandomDouble() {
		return rng.nextDouble();
	}
	
	// Returns false if we haven't finished yet
	// Returns true if we are done with our scientific activity so other activities know that we are ready to switch back such that they can proceed with their operations
	public boolean isReady() {
		return this.ready; 
	}
}
