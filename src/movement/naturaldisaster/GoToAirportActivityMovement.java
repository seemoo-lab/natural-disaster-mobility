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

import movement.map.SimMap;
import core.SimClock;

/**
 * The GoToAirportActivityMovement class represents individuals going from their actual location straight to the airport
 * 
 *  Individuals going to the airport either leave the country after the disaster accurred 
 *  as they are done with their disaster relief (or similar) activities or
 *  they are refugees fleeing the country to safer places.  
 *
 * @author Tom Schons
 */
public class GoToAirportActivityMovement extends MapBasedMovement implements SwitchableMovement {
	
	// Constants for importing settings from default settings file
	// Location files loaded via external default settings file (if provided)
	public static final String AIRPORT_LOCATIONS_FILE_SETTING = "airportLocationsFile";
	
	// File names of the specfic location files
	private String airportLocationsFile = null;
	
	// Lists of coordinates for specific location files
	private List<Coord> airport = null;
	
	// Holds the nodes chosen airport location he wants to go to - Randomly chosen in order to hold account to invariances in the real world
	private Coord someAirportLocation;
	
	// The mode in which we are operating this activity right now
	private int mode; 
	
	// Modes of operation
	// GO_TO_AIRPORT_MODE -> We're heading to the airport
	private static final int GO_TO_AIRPORT_MODE = 0;
	// AT_AIRPORT_IDLE_MODE -> We're ideling at the airport until the end of the simulation
	private static final int AT_AIRPORT_IDLE_MODE = 1; 
	
	// DijkstraPathFinder used for calculating our path 
	private DijkstraPathFinder pathFinder;
	
	// The exact timeslot when we (re-)started this activtiy 
	private double startedActivityTime;
	
	// LastLocation holds the last location a node visited (is initially set to NULL since we don't know the exact location at the time we finish the constructor - will always be updated)
	private Coord lastLocation;
	// NextLocation holds the next location a node will visit (will always be updated)
	private Coord nextLocation;  
	
	// To be set true if we're ready to start this activity --> Can be called from other classes via .Activate() function
	// Since we assume everybody in the disaster zone starts with sleeping this is set false by default here!
	private boolean start = false;
	
	 // To be set true if we're done with this activity  ---> Status can be requested via .isReady() function
	private boolean ready = false; 
	
	// Current time we still need to wait
	private double waitingTime = 0; 
	
	
	/**
	 * GoToAirportActivityMovement constructor
	 * @param settings
	 */
	public GoToAirportActivityMovement(Settings settings) {
		super(settings);
		pathFinder = new DijkstraPathFinder(null);
		
		// Loading settings via default settings file

		// Loading location files as specified via default settings file (has to be done via try-catch)
		try {	
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
		// Chose a random position for a main street point location
		int firstRandom = this.getRandom(0,this.airport.size()-1);
		this.someAirportLocation = this.airport.get(firstRandom).clone();
		} catch (Throwable t) {
			System.out.println("Reading the airport location file permanently failed");
			System.out.println("somePointLocation " + this.someAirportLocation);
		}
		
		// Last location can't be set at this point since we don't know were it will be -> has to be set when calling the activity via .setInitialLocation() 
		this.lastLocation = null;  
		
		// Set initial mode
		this.mode = GO_TO_AIRPORT_MODE;
		// Value of -1 means we haven't started yet
		this.startedActivityTime = -1;
	}

	/**
	 * Construct a new GoToAirportActivityMovement instance from a prototype
	 * @param prototype
	 */
	public GoToAirportActivityMovement(GoToAirportActivityMovement prototype) {
		super(prototype);
		this.pathFinder = prototype.pathFinder;
		
		// Loading settings via default settings file
		this.airport = prototype.getAirport();
		
		// Chose a random position for the airport location
		int firstRandom = this.getRandom(0,this.airport.size()-1);
		this.someAirportLocation = this.airport.get(firstRandom).clone();
		
		// Last location can't be set at this point since we don't know were it will be -> has to be set when calling the activity via .setInitialLocation()
		this.lastLocation = null; 
		
		// Set initial mode
		this.mode = GO_TO_AIRPORT_MODE; 
		// Value of -1 means we haven't started yet
		this.startedActivityTime = -1;
	}

	@Override
	public Path getPath() {
		// We only run into this if clause if a) the simulation has started b) our activity is activated right now
		if ((SimClock.getTime() > 0) && start) {
		switch (mode) {
			case GO_TO_AIRPORT_MODE: {
				// GO_TO_AIRPORT_MODE -> We're heading to the airport
				if (startedActivityTime == -1) {
					// We just started this activity
					// Generating a home waiting time
					this.waitingTime = generateHomeWaitTime(); 
					// Saving the exact timeslot we started this activity 
					this.startedActivityTime = SimClock.getTime();
				}
				if (SimClock.getTime() >= (this.startedActivityTime + this.waitingTime)) {
					// We're done waiting, we can now go to the airport

					// Now we can calculate the PATH to the airport

					// Selecting an airport location 
					int firstRandom = this.getRandom(0,this.airport.size()-1);
					// Setting nextLocation to airport location
					this.nextLocation = this.airport.get(firstRandom).clone();
					
					// Calculation of path to the airport
					SimMap map = super.getMap();
					if (map == null) {
						System.out.println("Error while getting map!");
						return null;
					}
					// Creating the path 
					Path path = new Path(generateSpeed());
					try {
						// From last location -> To the airport
						MapNode fromNode = map.getNodeByCoord(lastLocation); // Actual location
						MapNode toNode = map.getNodeByCoord(nextLocation); // Airport
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
					
					// Setting parameters for switching to next mode (AT_AIRPORT_IDLE_MODE)
					this.mode = AT_AIRPORT_IDLE_MODE;
					this.startedActivityTime = SimClock.getTime();
					// Creating a larger waiting time since we eitherway idle in the AT_AIRPORT_IDLE_MODE
					this.waitingTime = generateHomeWaitTime() * 5; 
					
					return path;
				}
				else {
					// We still need to idle since we haven't waited enough
					break;
				}
			}
			case AT_AIRPORT_IDLE_MODE: {
				// AT_AIRPORT_IDLE_MODE -> We're ideling at the airport until the end of the simulation
				if (SimClock.getTime() >= (this.startedActivityTime + this.waitingTime)) {
					// We can continue if the actual SimClock time is greater than the old startedActivtyTime plus the current waiting time
					// Reset parameteres in order to restart go to airport activity the next day
					this.ready = true; 
					this.start = false;
					this.mode = AT_AIRPORT_IDLE_MODE;
					
					this.startedActivityTime = SimClock.getTime();
					this.waitingTime = generateHomeWaitTime();
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
		// The reason is that we don't need this function in the go to airport activity, yet we can make use of it to step trough our programm 
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
		// We initially start at a random airport location -> this is only set temporarily since we don't know the exact initial position but the ONE would create null pointer exceptions otherwise 
		return someAirportLocation.clone();
	}
	
	public List<Coord> getAirport()
	{
		return this.airport; 
	}
	
	// Method required for setting a correct initial location before the activity is launched -> Mandatory (if not you'll get null pointer exceptions!)
	public void setInitialLocation(Coord location) {
		this.lastLocation = location;
	}
	
	public Coord getLastLocation() {
		return lastLocation.clone();
	}
	
	public Coord getSomeAirportLocation() {
		return someAirportLocation.clone();
	}
	
	// Get random int value, between provided min and max; returns 0 if invalid argument is provided 
	public int getRandom(int min, int max) {
		if ((min >= 0) && (max > 0)) {
			return rng.nextInt(max - min) + min;
		}
		return 0; 
	}

	// Get random double value, between 0.0 and 1.0
	public double getRandomDouble() {
		return rng.nextDouble();
	}
	
	// Function for (re-) activating our go to airport mode
	// Function is to be called at the end of any other activity such that we can (re-) activate the go to airport mode
	public void Activate() {
		// True -> means we can start this activity at anytime as of now
		start = true;
		// False -> means we're not yet finished with this activity
		ready = false;
	}

	// Returns false if we haven't finished yet
	// Returns true if we are done with our go to airport activity so other activities know that we are ready to switch back such that they can proceed with their operations
	public boolean isReady() {
		return this.ready; 
	}
}
