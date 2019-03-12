package uk.ac.nott.cs.g53dia.agent;

import uk.ac.nott.cs.g53dia.library.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;

/**
 * A simple example Tanker
 * 
 * @author Julian Zappala
 */
/*
 * Copyright (c) 2011 Julian Zappala
 * 
 * See the file "license.terms" for information on usage and redistribution of
 * this file, and for a DISCLAIMER OF ALL WARRANTIES.
 */
public class DemoTanker extends Tanker {
	int goToStation; int goToWell;
	Point nearestStation_point;

	public DemoTanker() {
		this(new Random());
	}

	/**
	 * The tanker implementation makes random moves. For reproducibility, it
	 * can share the same random number generator as the environment.
	 * 
	 * @param r
	 *            The random number generator.
	 */
	public DemoTanker(Random r) {
		this.r = r;
	}

	static class Coords{
		//This class will be used to represent found utilities; 
		//stores their points and global x and y coordinates as well as a string name
		int x;
		int y;
		Point point;
		String name;
		public Coords(int i, int j,Point point, String name) {
			this.x = i;
			this.y = j;    
			this.point = point;
			this.name = name;
		}

	}
	static class Goal{
		//A goal class storing information that can be mapped to an action 
		Point point;
		Coords coords;
		String name;
		public Goal(Point point, Coords coords,String name) {
			this.point = point;
			this.name = name;
			this.coords = coords;
		}
	}
	HashMap <Integer,Coords> allStationsMap = new HashMap<>();
	HashMap <Integer,Coords> allWellsMap = new HashMap<>();
	HashMap <Integer,Coords> allPumpsMap = new HashMap<>();

	int direction =0;
	int tankerDist=0; int tanker_xDist=0; int tanker_yDist=0;
	Boolean stationTask = false; Boolean wellTask = false;
	Coords activeStation = null; Coords wellFound = null; int sameStationTask=0;
	int stationHashId=0, wellHashId=0, pumpHashId=0;
	double wasteThreshold;
	
	static final String EXECUTE_GOAL_REFUEL = "Action_Refuel";
	static final String MOVE_DIR_REFUEL = "MoveTowards_Refuel";
	static final String MOVE_DIR_STATION = "MoveTowards_Station";
	static final String MOVE_DIR_WELL = "MoveTowards_Well";
	static final String EXECUTE_GOAL_LOAD_WASTE = "Action_getWasteTask";
	static final String EXECUTE_GOAL_DISPOSE_WASTE = "Action_disposeWaste";
	
	
	public Action senseAndAct(Cell[][] view, boolean actionFailed, long timestep) {
		wasteThreshold = MAX_WASTE * 70.0/100.0; //max waste level before need for disposing waste
		
		updateState(view); //update the agent's map of the environment

		Goal goal = formulateGoal(view); //returns a goal which is mapped to an action

		if(goal!=null) {
			if(goal.name.equals(EXECUTE_GOAL_REFUEL)) {
				return new RefuelAction();
			}else if(goal.name.equals(EXECUTE_GOAL_LOAD_WASTE)) {
				return loadWasteAction(view);
			}else if(goal.name.equals(EXECUTE_GOAL_DISPOSE_WASTE)) {
				return new DisposeWasteAction();
			}
			//System.out.println("GoalName: "+goal.name+ "TowardsX: "+goal.coords.x+ "TowardsY: "+goal.coords.y);
			return aMoveAction(goal, view);
		}


		//wander if no goal
		return aMoveAction(new Goal(null, null, "Wander"), view);
	}

	public LoadWasteAction loadWasteAction(Cell[][]view) {
		//System.out.println("Waste Loaded!");
		Station st = (Station)getCurrentCell(view);
		Task task = st.getTask();
		return new LoadWasteAction(task);

	}

	public Goal formulateGoal(Cell[][]view) {
		Goal goal = null; Coords nearestPump;

		//FUEL CHECK//
		Coords currentCoords= currentCoords(view);
		int tankerX = currentCoords.x-20+tanker_xDist; 
		int tankerY = 20-currentCoords.y+tanker_yDist;
		int nearestFuelpumpDist = 0; Boolean defaultPump = false;
		
		//Calculate distance to nearest fuelpump
		nearestPump = nearestPumporWell(allPumpsMap, view);
		if(nearestPump == null || 
				Math.max(Math.abs(tankerX),Math.abs(tankerY))<Math.max(Math.abs(nearestPump.x-tankerX), Math.abs(nearestPump.y-tankerY))) {
			//if no near pump or nearest pump is further than the default pump; set to default pump
			nearestFuelpumpDist = Math.max(tankerX, tankerY);
			defaultPump = true;
		}else {
			nearestFuelpumpDist = Math.max(Math.abs(nearestPump.x - tankerX), Math.abs(nearestPump.y - tankerY));
			defaultPump = false;
		}
		
		if(this.getFuelLevel()-4 <= nearestFuelpumpDist*2 && !(getCurrentCell(view) instanceof FuelPump)) {	
			if(defaultPump) {
				return new Goal(FUEL_PUMP_LOCATION, new Coords(0,0, FUEL_PUMP_LOCATION, FuelPump.class.getName()), MOVE_DIR_REFUEL);
			}else {
				return new Goal(nearestPump.point, nearestPump, MOVE_DIR_REFUEL);
			}
		}
		
		if(getCurrentCell(view) instanceof FuelPump && this.getFuelLevel()<MAX_FUEL) {
			direction = (direction+1) %8;
			if(stationTask) {sameStationTask++;} //DEAL WITH SAME STATION MULTIPLE ATTEMPTS
			return new Goal(getCurrentCell(view).getPoint(),null, EXECUTE_GOAL_REFUEL);
		}
		//END OF FUEL CHECK
		
		
		if(sameStationTask >=3) {
			//If failed attempt at getting to the same station, redirect the agent.
			System.out.println("same station make changes");
			stationTask = false;
			direction = (direction+1) %8;
			sameStationTask = 0;
		}
		
		//WASTE CAPACITY CHECK
		wellFound = nearestPumporWell(allWellsMap, view);
		if((this.getWasteLevel()>=wasteThreshold || wellTask==true) && !(getCurrentCell(view)instanceof Well)) {
			//at full waste capacity or if already en route to well; keep moving towards well
			if(wellFound!=null) {
				return new Goal(wellFound.point, wellFound, MOVE_DIR_WELL);
			}
		}

		if(getCurrentCell(view) instanceof Well && this.getWasteLevel()!=0) {
			//found well, reset boolean to indicate this and dispose waste
			wellTask = false;
			return new Goal(getCurrentCell(view).getPoint(), wellFound, EXECUTE_GOAL_DISPOSE_WASTE);
		}
		//END OF WASTE CAPACITY CHECK
		

		//ACTIVE STATION CHECK
		if(stationTask == true && activeStation!=null && !(getCurrentCell(view) instanceof Station )) {
			//If agent already has a task to complete
			Coords tempStation= findActiveStations(allStationsMap, view);//will return nearest currently seen active station
			if(tempStation!=null) {
				if(distanceToUtility(tempStation, view)<distanceToUtility(activeStation, view)) {
					//if agent finds an active station closer than the one currently en route to, swap and move to nearer station
					activeStation = tempStation;
				}	
				Station st =  (Station)view[activeStation.x+20-tanker_xDist][-(activeStation.y-20-tanker_yDist)];
				if(st.getTask().getWasteRemaining()>this.getWasteCapacity() && wellFound!=null) {
					//if not enough capacity to execute task, head for well instead
					wellTask = true; stationTask = false;
					return new Goal(wellFound.point, wellFound, MOVE_DIR_WELL);
				}else {wellTask = false;}
			}
			return new Goal(activeStation.point,activeStation, MOVE_DIR_STATION);
		}
		//if agent needs a new task 
		activeStation = findActiveStations(allStationsMap, view);//find nearest station we can currently see
		if(activeStation!=null && !(getCurrentCell(view)instanceof Station)) {//check for nearby station with task and set as goal
			stationTask = true;			
			Station st =  (Station)view[activeStation.x+20-tanker_xDist][-(activeStation.y-20-tanker_yDist)];
			if(st.getTask().getWasteRemaining()>this.getWasteCapacity() && wellFound!=null) {
				//Not enough waste capacity, head for a well instead
				wellTask = true; stationTask = false;
				return new Goal(wellFound.point, wellFound, MOVE_DIR_WELL);
			}else {wellTask = false;}
			
			return new Goal(activeStation.point,activeStation, MOVE_DIR_STATION);
		}
		//If agent has reached an active station
		if(getCurrentCell(view) instanceof Station ) {
			direction = (direction+1) %8;
			Station st = (Station)getCurrentCell(view);
			if(st.getTask()!=null && st.getTask().getWasteRemaining()<=this.getWasteCapacity()) {
				stationTask = false; activeStation = null;
				sameStationTask=0;
				return new Goal(getCurrentCell(view).getPoint(),null,EXECUTE_GOAL_LOAD_WASTE);
			}
		}


		return goal;
	}
	
	public int distanceToUtility(Coords utility, Cell[][]view) {
		Coords currentCoords= currentCoords(view);
		int tankerX = currentCoords.x-20+tanker_xDist; 
		int tankerY = 20-currentCoords.y+tanker_yDist;
		int distance = Math.max(Math.abs(utility.x-tankerX), Math.abs(utility.y-tankerY));
		return distance;
	}
	
	public void updateState(Cell[][]view) {
		//Updates the state for the immediate environment, storing all utilities found. Also stores utilities seen previously in their unique hashmaps so 
		//the agent can thus build up on its perceived environment	
		for(int i=0; i<view.length; i++) {
			for(int j=0; j<view.length; j++) {
				int x= i-20+tanker_xDist; //get global coordinates of the cell
				int y = 20-j+tanker_yDist;
				if(view[i][j].getClass()==(Station.class)) {//store station location in it's hashmap if found
					Coords newUtility = new Coords(x,y,view[i][j].getPoint(), Station.class.getName());
					if(!isUtilitySavedAlready(newUtility, allStationsMap)) {
						allStationsMap.put( stationHashId, newUtility ) ;
						stationHashId++;
					}else {
						//update station and thus task
						updateStationUtility(newUtility, allStationsMap);
					}
				}else if(view[i][j].getClass().equals(Well.class)){//store well location in it's hashmap if found
					Coords newUtility = new Coords(x,y,view[i][j].getPoint(), Well.class.getName());
					if(!isUtilitySavedAlready(newUtility, allWellsMap)) {
						allWellsMap.put( wellHashId, newUtility );
						wellHashId++;
					}
				}else if(view[i][j].getClass().equals(FuelPump.class)) {//store pump location in it's hashmap if found
					Coords newUtility = new Coords(x,y,view[i][j].getPoint(),FuelPump.class.getName());
					if(!isUtilitySavedAlready(newUtility, allPumpsMap)) {
						allPumpsMap.put( pumpHashId, newUtility);
						pumpHashId++;
					}
				}
			}

		}

		return;
	}

	
	public void updateStationUtility(Coords newUtility, HashMap<Integer, Coords>utilityMap) {
		//find and update station utility ; useful if say a station now has a task
		for(Map.Entry<Integer,Coords> entry: utilityMap.entrySet()) {
			Coords utility = entry.getValue();
			int key = entry.getKey();
			if(utility.point.equals(newUtility.point)) {
				utilityMap.replace(key, utility);
			}
		}
	}
	public Boolean isUtilitySavedAlready (Coords newUtility, HashMap<Integer, Coords> utilityMap) {
		for(Map.Entry<Integer,Coords> entry: utilityMap.entrySet()) {
			Coords utility = entry.getValue();
			if(utility.point.equals(newUtility.point)) {
				return true;
			}
		}
		return false;
		
	}
	public Coords nearestPumporWell (HashMap<Integer, Coords> utilityMap, Cell[][]view) {
		//Used to find the nearest pump or well utility and return a Coord object
		Coords utilityCoords;
		int minDistance=30000; //just need an initially ridiculously large number
		int minDistKey = -1;
		Coords tankerCoords = currentCoords(view);
		int tankerX = tankerCoords.x-20+tanker_xDist; 
		int tankerY = 20-tankerCoords.y+tanker_yDist;
		for(Map.Entry<Integer, Coords> entry:utilityMap.entrySet()) {
			Coords utility = entry.getValue();
			int key = entry.getKey();
			int dist = Math.max(Math.abs(utility.y-tankerY), Math.abs(utility.x-tankerX));
			if(dist<minDistance) {
				minDistKey =key;
				minDistance = dist;
			}
		}
		if(minDistKey != -1) {
			utilityCoords = utilityMap.get(minDistKey);
			return utilityCoords;
		}
		return null;
	}

	public Coords findActiveStations(HashMap<Integer, Coords> stationsMap, Cell[][]view){
		//Returns a Coords object of the nearest station that currently has a task
		int count = 0;
		int minDistance =30000; 
		Coords tankerCoords = currentCoords(view);
		int tankerX = tankerCoords.x-20+tanker_xDist; 
		int tankerY = 20-tankerCoords.y+tanker_yDist;
		Coords stCoords = null;
		Station st = null;
		Task task = null;
		HashMap<Integer, Coords> activeStations = new HashMap<>();
		for(Map.Entry<Integer, Coords> entry:stationsMap.entrySet()) {
			//find all active stations in the vicinity
			Coords stationCoords = entry.getValue();
			if(stationCoords.name.equals(Station.class.getName())) {
				try{
					st =  (Station)view[stationCoords.x+20-tanker_xDist][-(stationCoords.y-20-tanker_yDist)];
					task = st.getTask();
				}catch(Exception e) {
					continue;
				}
				if(task != null) {
					activeStations.put(count, stationCoords);
					count++;
				}
			}
		}

		if(!activeStations.isEmpty()) {
			//we found 'active' stations with tasks now to find the closest to the agent.
			for(Map.Entry<Integer, Coords> entry:activeStations.entrySet()) {
				Coords stationActive = entry.getValue();
				int dist = Math.max(Math.abs(stationActive.y-tankerY), Math.abs(stationActive.x-tankerX));
				if(dist<minDistance) {
					minDistance = dist;
					stCoords = entry.getValue();
				}
			}

		}

		return stCoords;
	}

	public Coords currentCoords(Cell[][]view) {
		//Returns the current coordinates of the agent
		Coords agentCoords = null;

		for(int i=0; i<view.length; i++) {
			for(int j=0; j<view.length; j++) {
				if(view[i][j].getPoint()==(getCurrentCell(view).getPoint())) {
					agentCoords = new Coords(i, j, view[i][j].getPoint(), "currentLoc");
				}
			}
		}
		return agentCoords;

	}


	public MoveAction aMoveAction(Goal g, Cell[][]view) {
		//Utilised the moveAction function by passing to it a direction calculated based on the displacement to the target location
		final int NORTH = 0, SOUTH = 1, EAST = 2, WEST = 3, 
				NORTHEAST = 4, NORTHWEST = 5, SOUTHEAST = 6, SOUTHWEST = 7;

		if(g.name.equals("Wander")) {
			if(direction==0) {tanker_yDist++;}
			else if(direction==1) {tanker_yDist--;}
			else if(direction==2) {tanker_xDist++;}
			else if(direction==3) {tanker_xDist--;}
			else if(direction==4) {tanker_xDist++; tanker_yDist++;}
			else if(direction==5) {tanker_xDist--; tanker_yDist++;}
			else if(direction==6) {tanker_xDist++; tanker_yDist--;}
			else if(direction==7) {tanker_xDist--; tanker_yDist--;}
			tankerDist++;
			return new MoveAction(direction);
		}

		int currX = tanker_xDist;
		int currY = tanker_yDist;
		int dir = 0;
		int targetX = g.coords.x;
		int targetY = g.coords.y;
		int dx = targetX - currX;
		int dy = targetY - currY;

		if(dx>0 && dy>0) {
			targetX++; targetY++; tanker_xDist++; tanker_yDist++;
			return new MoveAction(NORTHEAST);
		}else if(dx<0 && dy>0) {
			targetX--; targetY++; tanker_xDist--; tanker_yDist++;
			return new MoveAction(NORTHWEST);
		}else if(dx>0 && dy<0) {
			targetX++; targetY--; tanker_xDist++; tanker_yDist--;
			return new MoveAction(SOUTHEAST);
		}else if(dx<0 && dy<0) {
			targetX--; targetY--; tanker_xDist--; tanker_yDist--;
			return new MoveAction(SOUTHWEST);
		}
		
		if (dx < 0) {
			tanker_xDist--;
			return new MoveAction(WEST);
		} else if (dx > 0) {
			tanker_xDist++;
			return new MoveAction(EAST);
		}
		if (dy < 0) {
			tanker_yDist--;
			return new MoveAction(SOUTH);
		} else if (dy > 0) {
			tanker_yDist++;
			return new MoveAction(NORTH);
		}
		tankerDist = Math.max(tanker_xDist, tanker_yDist);

		return new MoveAction(dir);
	}


}


