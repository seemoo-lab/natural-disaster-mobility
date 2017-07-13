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
import core.SimClock;
import input.WKTReader;
import java.io.File;
import java.util.LinkedList;
import java.util.List;
import movement.map.SimMap;
import java.util.Random;

/**
 * SleepActivityMovement class
 * Activity is done everyday in the evening by everyone in a disaster area.
 *
 * Activity is called when previous activity finishes
 * SleepActivityMovement makes individuals sleep for defined period (with random offset) in their home;
 * Activity then switches to next activity after finishing (e.g. starting the next day)
 * 
 * @author Tom Schons
 */
public class SleepActivityMovement extends MapBasedMovement implements SwitchableMovement {

	// Constants for importing settings from default settings file
	public static final String SLEEPING_TIME_MIN = "sleepingTimeMin";
	public static final String SLEEPING_TIME_MAX = "sleepingTimeMax";
	public static final String DAY_LENGTH = "dayLength";
	public static final String NUMBER_OF_DAYS = "nbrOfDays";
	public static final String OFFSET_START_DELAY = "offsetStartDelay";
	// Seed used for initializing the scenario's random number generator -> The same seed will always produce the same output which is mandatory for creating reproducable results!
	public static final String RNGSEED = "rngSeed";
	
	 // Only true if we're done with sleeping
	private boolean doneSleeping;
	
	 // Minimum sleeping time an individual has (in seconds)
	private double sleepingTimeMin;
	 // Maximum sleeping time an individual has (in seconds)
	private double sleepingTimeMax; 
	// Length of the day in seconds
	private double dayLength;  
	// Number of days
	private int nbrOfDays; 
	// Offset that is added to delay the start of this particular activity 
	private double offsetStartDelay;
	// Seed used for initializing the activity's random number generator such that the same seed will always produce the same output! 
	private long rngSeed; 
	
	// The exact timeslot when we (re-)started this activtiy 
	private double startedActivityTime;
	
	// Local variable holding the value of the time we still need to sleep
	private double sleepTime = -1; 
	
	// To be set true if we're ready to start this activity --> Can be called from other classes via .Activate() function
	// Since we assume everybody in the disaster zone starts with sleeping this is set true by default here!
	// Everybody with "arrival at airport" activites will not start with sleeping and thus start is set false in that activity 
	private boolean start = true;
	
	 // To be set true if we're done with this activity  ---> Status can be requested via .isReady() function
	private boolean ready = false; 
	
	// Random generator initalized with seed (if provided via settings file)
	private Random rand;
	
	// Local day counter 
	private int dayCounter; 
	
	/**
	 * SleepActivityMovement constructor
	 * @param settings
	 */
	public SleepActivityMovement(Settings settings) {
		super(settings);
		
		// Loading settings via default settings file
		if (settings.contains(SLEEPING_TIME_MIN)) {
			this.sleepingTimeMin = settings.getDouble(SLEEPING_TIME_MIN);
		}
		else {
			System.out.println("You didn't specify a value for the minimum sleeping time!");
			System.out.println("sleepingTimeMin: " + this.sleepingTimeMin); 
		}
		if (settings.contains(SLEEPING_TIME_MAX)) {
			this.sleepingTimeMax = settings.getDouble(SLEEPING_TIME_MAX);
		}
		else {
			System.out.println("You didn't specify a value for the maximum sleeping time!");
			System.out.println("sleepingTimeMax: " + this.sleepingTimeMax); 
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
		if (settings.contains(OFFSET_START_DELAY)) {
			this.offsetStartDelay = settings.getDouble(OFFSET_START_DELAY);
		}
		else {
			System.out.println("You didn't specify a value for the offset start delay!");
			System.out.println("offsetStartDelay: " + this.offsetStartDelay); 
		}
		if (settings.contains(RNGSEED)) {
			this.rngSeed = settings.getLong(RNGSEED);
		}
		else {
			System.out.println("You didn't specify a value as a seed for the random number generator!");
			System.out.println("rngSeed: " + this.rngSeed); 
		}
		
		// Create a new random generator for this activity with the provided seed -> Important for ensuring reproducable results! 
		this.rand = new Random(this.rngSeed);  
		// Generating our own sleep time
		this.sleepTime = (this.sleepingTimeMax * this.getRandomDouble()) + (this.sleepingTimeMin * this.getRandomDouble()); 
		// Checking boundaries of sleep time
		if (this.sleepTime < this.sleepingTimeMin) {
			this.sleepTime = this.sleepingTimeMin;
		}
		if (this.sleepTime > this.sleepingTimeMax) {
			this.sleepTime = this.sleepingTimeMax;
		}
		
		// Adding potential offset (provided via default settings file) for the first night of sleep
		this.sleepTime += this.offsetStartDelay; 
		System.out.println("Calculated initial sleep time is: " + this.sleepTime); 

		// Since we start our day sleeping at home -> we're not yet done sleeping
		this.doneSleeping = false; 
		this.startedActivityTime = -1; 
		this.dayCounter = 0;
	}

	/**
	 * Construct a new SleepActivityMovement instance from a prototype
	 * @param proto
	 */
	public SleepActivityMovement(SleepActivityMovement prototype) {
		super(prototype);
		// Loading settings via default settings file
		this.rand = prototype.getRand(); 
		this.sleepingTimeMin = prototype.getSleepingTimeMin(); 
		this.sleepingTimeMax = prototype.getSleepingTimeMax(); 
		this.dayLength = prototype.getDayLength(); 
		this.nbrOfDays = prototype.getNbrOfDays(); 
		this.offsetStartDelay = prototype.getOffsetStartDelay(); 
		
		// Generating our own sleep time
		this.sleepTime = (this.sleepingTimeMax * this.getRandomDouble()) + (this.sleepingTimeMin * this.getRandomDouble()); 
		// Checking boundaries of sleep time
		if (this.sleepTime < this.sleepingTimeMin) {
			this.sleepTime = this.sleepingTimeMin;
		}
		if (this.sleepTime > this.sleepingTimeMax) {
			this.sleepTime = this.sleepingTimeMax;
		}
		
		// Adding potential offset (provided via default settings file) for the first night of sleep
		this.sleepTime += this.offsetStartDelay; 
		System.out.println("Calculated initial sleep time is: " + this.sleepTime); 
		
		// Since we start our day sleeping at home -> we're not yet done sleeping
		this.doneSleeping = false; 
		this.startedActivityTime = -1; 
		this.dayCounter = 0;
	}

	@Override
	public Path getPath() {
		// Calculating wheather or not we finished our sleeping activity
		// We only run into this if clause if a) the simulation has started b) our activity is activated right now c) the simulation hasn't reached it's end yet 
		if ((SimClock.getTime() > 0) && (start) && (this.dayCounter != this.nbrOfDays)) {    
				if (SimClock.getTime() > ((this.dayCounter * this.dayLength) + this.sleepTime)) {
					// If we are here we are sure that we slept enough for today
					System.out.println("Done sleeping -> getting back to work now!");
					System.out.println("this.dayCounter --- " + this.dayCounter);
					System.out.println("this.dayLength --- " + this.dayLength);
					System.out.println("this.dayCounter * this.dayLength --- " + ((this.dayCounter * this.dayLength) + this.sleepTime));
					this.doneSleeping = true;
					this.ready = true;
					this.start = false;
				}
				else if (SimClock.getTime() < ((this.dayCounter * this.dayLength) + this.sleepTime)) {
					// If we are here we are sure that we still need to sleep a bit more for today
					// So me just idle here 
					System.out.println("Still sleeping!");
				}
			}

		if (this.dayCounter == this.nbrOfDays) {
			// If we arrived here than the simulation is over since we reached max. days provided via settings file
			// We simulate this by sleeping forever!
			this.ready = false;
			this.start = false; 
			this.doneSleeping = false;
			this.sleepTime = -1; 
			System.out.println("Simulation is over - Node sleeps forever now!");
			this.sleepTime = Integer.MAX_VALUE; 
			if (SimClock.getTime() >= this.sleepTime) {
				// We reached the end of the universe ;)  
				System.out.println("You reached the end of Integer.MAX_VALUE!");
			}
		}
		else if (!start) {
			// Done sleeping, we want to switch back to another activtiy be the main activity hasn't switched movement models yet  
			System.out.println("Sleeping is over - Waiting for main activity to be called again with the next iteration!");
		}
		return null; 
	}
	
	// Function to be called by other classes to update our local day counter if necessary
	public void updateDayCounter(int dayCounter) {
		this.dayCounter = dayCounter; 
	}
	
	// Function for (re-) activating our sleep mode
	// Function is to be called at the end of any other activity such that we can (re-) activate the sleep mode
	public void Activate(int dayCounter) {
		this.dayCounter = dayCounter;
		// True -> means we can start this activity at anytime as of now
		start = true;
		// False -> means we're not yet finished with this activity
		ready = false;
		doneSleeping = false;
	}

	// Returns false if we haven't slept (engough) yet
	// Returns true if we are done sleeping so other activities know that we are ready to switch back such that they can proceed with their operations
	public boolean isReady() {
		if (this.doneSleeping) {
			this.ready = true; 
		}
		return this.ready; 
	}
	
	@Override
	protected double generateWaitTime() {
		// Since our sleep time is more or less fixed by the parameters loaded from the external default settings file we don't really need this function
		// Especially since generateWaitTime() is called by internal functions of the ONE after each getPath() method has returned, so we just use this here as a simple step counter
		// The reason is that we don't need this function in the sleep activity, yet we can make use of it to step trough our programm 
		// The value of 100 is arbitrary, yet values shouln't be too big or small in order that the ONE operates properly
		return 100;
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
	
	public double getSleepingTimeMin() {
		return this.sleepingTimeMin; 
	}
	
	public double getSleepingTimeMax() {
		return this.sleepingTimeMax; 
	}
	
	public double getDayLength() {
		return this.dayLength; 
	}
	
	public int getNbrOfDays() {
		return this.nbrOfDays; 
	}
	
	public double getOffsetStartDelay() {
		return this.offsetStartDelay; 
	}

}
