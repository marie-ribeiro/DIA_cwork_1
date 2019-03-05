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
		int x;
		int y;
		int distance;
		Point point;
		String name;
		public Coords(int i, int j,Point point, int distance, String name) {
			this.x = i;
			this.y = j;    
			this.point = point;
			this.distance = distance;
			this.name = name;
		}

	}
	static class Goal{
		Point point;
		Coords coords;
		String name;
		public Goal(Point point, Coords coords,String name) {
			this.point = point;
			this.name = name;
			this.coords = coords;
		}
	}
	HashMap <Integer,Coords> immediateMap = new HashMap<>();//stores the agents currently viewed percepts and it's own location
	HashMap <Integer,Coords> allStationsMap = new HashMap<>();
	HashMap <Integer,Coords> allWellsMap = new HashMap<>();
	HashMap <Integer,Coords> allPumpsMap = new HashMap<>();

	HashMap<Coords, String> utilityMap;
	int agentDistanceFromOrigin=0; int direction =0;
	/*
	 * The following is a simple demonstration of how to write a tanker. The
	 * code below is very stupid and simply moves the tanker randomly until the
	 * fuel tank is half full, at which point it returns to a fuel pump.
	 */
	Boolean goalSet = false; Coords goalCoords= null; String goalClass= null; Boolean gotFuel = false; Point nearestUtilityPoint;
	Boolean resetVars = false;
	Boolean arrived = null;

	int currStepCount = 0;
	Boolean stationTask = false; Boolean wellTask = false;
	Coords activeStation = null; Coords wellFound = null; int sameStationTask=0;
	int stationHashId=0, wellHashId=0, pumpHashId=0;
	double fuelThreshold; double wasteThreshold;
	PriorityQueue<String> pQueue =new PriorityQueue<String>();
	static final String EXECUTE_GOAL_REFUEL = "Action_Refuel";
	static final String MOVE_DIR_REFUEL = "MoveTowards_Refuel";
	static final String MOVE_DIR_STATION = "MoveTowards_Station";
	static final String MOVE_DIR_WELL = "MoveTowards_Well";
	static final String EXECUTE_GOAL_LOAD_WASTE = "Action_getWasteTask";
	static final String EXECUTE_GOAL_DISPOSE_WASTE = "Action_disposeWaste";

	int tankerDist=0; int tanker_xDist=0; int tanker_yDist=0;
	int attemptswithCurrentFuelCap=0;
	public Action senseAndAct(Cell[][] view, boolean actionFailed, long timestep) {
		fuelThreshold = MAX_FUEL *50.0 / 100.0; // minimum fuel level before refuel in %60 was good
		wasteThreshold = MAX_WASTE * 80.0/100.0; //max waste level before need for disposing waste
		
		updateState(view); //update the agent's map of the environment

		if(pQueue.isEmpty()) {
			//Do some initialisation
			pQueue.add("Wander");
			pQueue.add("Pickup");
			pQueue.add("Well");
			pQueue.add("Refuel");
		}

		//  System.out.println("!!!!!!!!!!!!!tankerXDIST:"+ tanker_xDist+" "+ "tankeryDisy: " + tanker_yDist);

		Goal goal = formulateGoal(view);

		if(goal!=null) {
			if(goal.name.equals(EXECUTE_GOAL_REFUEL)) {
				currStepCount=0;
				return new RefuelAction();
			}else if(goal.name.equals(EXECUTE_GOAL_LOAD_WASTE)) {
				return loadWasteAction(view);
			}else if(goal.name.equals(EXECUTE_GOAL_DISPOSE_WASTE)) {
				return new DisposeWasteAction();
			}
			//System.out.println("GoalName: "+goal.name+ "TowardsX: "+goal.coords.x+ "TowardsY: "+goal.coords.y);
			currStepCount++;
			return new MoveTowardsAction(goal.point);
			//return aMoveAction(goal, view);
		}


		//wander if no goal
		currStepCount++;
		return new MoveAction(direction);
		//return aMoveAction(new Goal(null, null, "Wander"), view);
	}

	public LoadWasteAction loadWasteAction(Cell[][]view) {
		System.out.println("Waste Loaded!");
		Station st = (Station)getCurrentCell(view);
		Task task = st.getTask();
		return new LoadWasteAction(task);

	}

	public Goal formulateGoal(Cell[][]view) {
		Goal goal = null; Coords nearestPump;

		//FUEL CHECK
		Coords currentCoords= currentCoords(view);
		//if(this.getFuelLevel() <= fuelThreshold && !(getCurrentCell(view) instanceof FuelPump ))  {//check for sufficient fuel
		if(currStepCount*2 >= this.getFuelLevel()-3 && !(getCurrentCell(view) instanceof FuelPump)) {
			nearestPump = nearestPumporWell(allPumpsMap);
			if(nearestPump == null) {
				System.out.println("FUELpumpLOC!: "+FUEL_PUMP_LOCATION.toString());
				return new Goal(FUEL_PUMP_LOCATION,new Coords(0,0, FUEL_PUMP_LOCATION,Math.max(currentCoords.x, currentCoords.y),FuelPump.class.getName()), MOVE_DIR_REFUEL);
			}
			if(Math.max(currentCoords.x, currentCoords.y)<Math.max(nearestPump.x-currentCoords.x, nearestPump.y-currentCoords.y)) {//distance back to main fuel pump vs distance to new pump
				return new Goal(FUEL_PUMP_LOCATION,nearestPump,MOVE_DIR_REFUEL);
			}else {
				return new Goal(nearestPump.point,nearestPump,MOVE_DIR_REFUEL);
			}
		}
		if(getCurrentCell(view) instanceof FuelPump && this.getFuelLevel()<this.fuelThreshold) {
			direction = (direction+1) %8;
			if(stationTask) {sameStationTask++;} //DEAL WITH SAME STATION MULTIPLE ATTEMPTS
			return new Goal(getCurrentCell(view).getPoint(),null, EXECUTE_GOAL_REFUEL);
		}

		
		if(attemptswithCurrentFuelCap>=5) {
			System.out.println("More than 5 attempts to find stations with right amount of waste for current capacity");
			stationTask = false;
			wellTask = true;
			attemptswithCurrentFuelCap = 0;
		}
		if(sameStationTask >=5) {
			System.out.println("same station make changes");
			//stationTask = false;
			Coords replacementSt = findActiveStations(allStationsMap, view);
			while(replacementSt==(activeStation)) {
				replacementSt = findActiveStations(allStationsMap, view);
			}
			activeStation = replacementSt;
			//??//sameStationTask = 0;
			//direction = (direction+1) %8;
		}
		
		//WASTE CAPACITY CHECK
		wellFound = nearestPumporWell(allWellsMap);
		if((this.getWasteLevel()>=wasteThreshold || wellTask==true) && !(getCurrentCell(view)instanceof Well)) {
			//at full waste capacity search for well
			if(this.getWasteLevel()>=wasteThreshold ) {System.out.println("waste threshold reached!"+wasteThreshold);}
			if(wellFound!=null) {
				return new Goal(wellFound.point, wellFound, MOVE_DIR_WELL);
			}
		}

		if(getCurrentCell(view) instanceof Well && this.getWasteLevel()!=0) {
			wellTask = false;
			return new Goal(getCurrentCell(view).getPoint(), wellFound, EXECUTE_GOAL_DISPOSE_WASTE);
		}



		//ACTIVE STATION CHECK
		if(stationTask == true && activeStation!=null && !(getCurrentCell(view) instanceof Station )) {
			//task to complete
			System.out.println("INCOMPLETE STATION TASK!!!!!!!!!!!!!!");
			Coords tempStation= findActiveStations(allStationsMap, view);
			if(tempStation!=null) {
				//compare new station and old station and go to whichever is closer
				if(tempStation.distance<activeStation.distance) {
					System.out.println("swap station!");
					activeStation = tempStation;
				}
			}
			return new Goal(activeStation.point,activeStation, MOVE_DIR_STATION);
		}
		//Station bound: to complete a task if available
		activeStation = findActiveStations(allStationsMap, view);//(allStationsMap, view);
		if(activeStation!=null && !(getCurrentCell(view)instanceof Station)) {//check for nearby station with task and set as goal
			System.out.println("FOUND active STATION!!!"+ activeStation.x+"y:"+activeStation.y);
			stationTask = true;
			return new Goal(activeStation.point,activeStation, MOVE_DIR_STATION);
		}
		if(getCurrentCell(view) instanceof Station ) {
			direction = (direction+1) %8;
			Station st = (Station)getCurrentCell(view);
			if(st.getTask()!=null && st.getTask().getWasteRemaining()<this.getWasteCapacity()) {
				stationTask = false; activeStation = null;
				attemptswithCurrentFuelCap=0;
				sameStationTask=0;
				return new Goal(getCurrentCell(view).getPoint(),null,EXECUTE_GOAL_LOAD_WASTE);
			}else if(st.getTask()!=null && st.getTask().getWasteRemaining()>this.getWasteCapacity()) {
				System.out.println("too much waste here");
				attemptswithCurrentFuelCap++;
			}
		}


		return goal;
	}
	public void updateState(Cell[][]view) {
		//Updates the state for the immediate environment, storing all utilities found. Also stores utilities seen preiviously in their unique hashmaps so 
		//the agent can thus build up on its perceived environment
		Coords currCoords = currentCoords(view);
		stationHashId=0; wellHashId=0; pumpHashId=0; //xyDisp[0], xyDisp[1]

		for(int i=0; i<view.length; i++) {
			for(int j=0; j<view.length; j++) {
				System.out.println("something:"+ view[i][j].getPoint().toString()+"my i:"+i+"j:"+j);
				int[] xyDisp = new int[2];
				xyDisp = xyDisplacement(currCoords.x, currCoords.y, i, j);
				immediateMap.put(i, new Coords(i,j,view[i][j].getPoint(), Math.max(xyDisp[0], xyDisp[1]),view[i][j].getClass().getName() ));//can use same key as immediateMap will be re-written each time updateState is called
				if(view[i][j].getClass()==(Station.class)) {//store station location in it's hashmap if found
					allStationsMap.put( stationHashId, new Coords(i,j,view[i][j].getPoint(),Math.max(i-currCoords.x, j-currCoords.x), Station.class.getName()) );
				}else if(view[i][j].getClass().equals(Well.class)){//store well location in it's hashmap if found
					allWellsMap.put( wellHashId, new Coords(i,j,view[i][j].getPoint(),Math.max(i-currCoords.x, j-currCoords.x), Well.class.getName()) );
					wellHashId++;
				}else if(view[i][j].getClass().equals(FuelPump.class)) {//store pump location in it's hashmap if found
					allPumpsMap.put( pumpHashId, new Coords(i,j,view[i][j].getPoint(),Math.max(i-currCoords.x, j-currCoords.x), FuelPump.class.getName()) );
					pumpHashId++;
				}
			}

		}

		return;
	}

	public Coords nearestPumporWell (HashMap<Integer, Coords> utilityMap) {
		Coords utilityCoords;
		int minDistance=30000; //
		int minDistKey = -1;
		for(Map.Entry<Integer, Coords> entry:utilityMap.entrySet()) {
			Coords utility = entry.getValue();
			int key = entry.getKey();
			int dist = utility.distance;
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
		int count = 0;
		int minDistance = 30000; 
		Coords stCoords = null;
		Station st = null;
		Task task = null;
		HashMap<Integer, Coords> activeStations = new HashMap<>();
		for(Map.Entry<Integer, Coords> entry:stationsMap.entrySet()) {
			//find all active stations in the vicinity
			Coords stationCoords = entry.getValue();
			if(stationCoords.name.equals(Station.class.getName())) {
				try{
					st =  (Station)view[stationCoords.x][stationCoords.y];
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
				int dist = stationActive.distance;
				if(dist<minDistance) {
					minDistance = dist;
					stCoords = entry.getValue();
				}
			}

		}

		return stCoords;
	}

	public Coords currentCoords(Cell[][]view) {
		//Returns an object of the current coordinates of the agent relative to its immediate environment
		Coords agentCoords = null;

		for(int i=0; i<view.length; i++) {
			for(int j=0; j<view.length; j++) {
				if(view[i][j].getPoint()==(getCurrentCell(view).getPoint())) {
					agentCoords = new Coords(i, j, view[i][j].getPoint(), 0, "currentLoc");
				}
			}
		}
		return agentCoords;

	}

	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	//sought to use my own distance calculation function
	/* public void updateState(Cell[][]view) {
        //Updates the state for the immediate environment, storing all utilities found. Also stores utilities seen preiviously in their unique hashmaps so 
        //the agent can thus build up on its perceived environment
        Coords currCoords = currentCoords(view);
        int currDist = currCoords.distance;

        for(int i=0; i<view.length; i++) {
            for(int j=0; j<view.length; j++) {
                int[] xyDisp = new int[2];

                xyDisp = xyDisplacement(currCoords.x, currCoords.y, i, j);

                int utilityImmediateDist = Math.max(i,j);
                immediateMap.put(i, new Coords(i,j,view[i][j].getPoint(), utilityImmediateDist,view[i][j].getClass().getName() ));//can use same key as immediateMap will be re-written each time updateState is called
                //int utilityImmediateDist = Math.max(xyDisp[0],xyDisp[1]);
                //immediateMap.put(i, new Coords(tanker_xDist+xyDisp[0],tanker_yDist+xyDisp[1],view[i][j].getPoint(), utilityImmediateDist,view[i][j].getClass().getName() ));//can use same key as immediateMap will be re-written each time updateState is called

                if(view[i][j].getClass()==(Station.class)) {//store station location in it's hashmap if found
                    allStationsMap.put( stationHashId, new Coords(i,j,view[i][j].getPoint(),currDist+utilityImmediateDist, Station.class.getName()) );
                    //allStationsMap.put(stationHashId, new Coords(tanker_xDist+xyDisp[0],tanker_yDist+xyDisp[1], view[i][j].getPoint(),tankerDist+utilityImmediateDist, Station.class.getName()) );
                    //stationHashId++;
                    //System.out.println("i:"+i+" j:"+j+ " point:"+view[i][j].getPoint().toString());
                }else if(view[i][j].getClass().equals(Well.class)){//store well location in it's hashmap if found
                    allWellsMap.put( wellHashId, new Coords(i,j,view[i][j].getPoint(),currDist+utilityImmediateDist, Well.class.getName()) );
                    //allWellsMap.put( wellHashId, new Coords(tanker_xDist+xyDisp[0],tanker_yDist+xyDisp[1],view[i][j].getPoint(),tankerDist+utilityImmediateDist,Well.class.getName()) );
                    wellHashId++;
                }else if(view[i][j].getClass().equals(FuelPump.class)) {//store pump location in it's hashmap if found
                    allPumpsMap.put( pumpHashId, new Coords(i,j,view[i][j].getPoint(),currDist+utilityImmediateDist, FuelPump.class.getName()) );
                    //allPumpsMap.put( pumpHashId, new Coords(tanker_xDist+xyDisp[0],tanker_yDist+xyDisp[1],view[i][j].getPoint(),tankerDist+utilityImmediateDist,FuelPump.class.getName()) );
                    pumpHashId++;
                }
            }

        }

        return;
    }*/

	public int[] xyDisplacement(int startX, int startY, int targetX, int targetY) {
		int[] xyDispVal = new int[2];
		int dispX = 0;
		int dispY = 0;
		while(startX!=targetX && startY!=targetY) {
			if(targetX>startX) {
				dispX++; startX++;
			}else if(targetX<startX) {
				dispX--; startX--;
			}
			if(targetY>startY) {
				dispY++; startY++;
			} if(targetY<startY) {
				startY--; dispY--;
			}
			if(targetX>startX && targetY>startY) {
				startX++; dispX++; startY++; dispY++; //Northeast
			}else if(targetX<startX && targetY>startY) {
				startX--; dispX--; startY++; dispY++; //Northwest                
			}else if(targetX<startX && targetY<startY) {
				startX--; dispX--; startY--; dispY--; //Southwest                
			}else if(targetX>startX && targetY<startY) {
				startX++; dispX++; startY--; dispY--;//Southeast
			}

		}

		xyDispVal[0] = dispX;
		xyDispVal[1] = dispY;
		return xyDispVal;
	}

	public MoveAction aMoveAction(Goal g, Cell[][]view) {
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
			//System.out.println("tankDist:"+ tankerDist+" xdist:"+tanker_xDist+" yDist: "+tanker_yDist);
			return new MoveAction(direction);
		}

		int currX = tanker_xDist;//currentCoords(view).x;
		int currY = tanker_yDist;//currentCoords(view).y;
		int dir = 0;
		int targetX = g.coords.x; 
		int targetY = g.coords.y;
		System.out.println("Destination:"+g.name+" " +"x: "+g.coords.x+"y:"+g.coords.y);
		int dx = targetX - currX;
		int dy = targetY - currY;

		if(dx>0 && dy>0) {
			targetX++; targetY++; tanker_xDist++; tanker_yDist++;
			dir= NORTHEAST;
		}else if(dx<0 && dy>0) {
			targetX--; targetY++; tanker_xDist--; tanker_yDist++;
			dir = NORTHWEST;
		}else if(dx>0 && dy<0) {
			targetX++; targetY--; tanker_xDist++; tanker_yDist--;
			dir = SOUTHEAST;
		}else if(dx<0 && dy<0) {
			targetX--; targetY--; tanker_xDist--; tanker_yDist--;
			dir= SOUTHWEST;
		}
		if (dx < 0) {
			tanker_xDist--;
			dir =WEST;
		} else if (dx > 0) {
			tanker_xDist++;
			dir=EAST;
		}
		if (dy < 0) {
			tanker_yDist--;
			dir=SOUTH;
		} else if (dy > 0) {
			tanker_yDist++;
			dir=NORTH;
		}
		tankerDist = Math.max(tanker_xDist, tanker_yDist);
		System.out.println("tankDist:"+ tankerDist+" xdist:"+tanker_xDist+" yDist: "+tanker_yDist);

		return new MoveAction(dir);
	}

	public Boolean atLocation(String className, Cell curr) throws ClassNotFoundException {
		if(Class.forName(className)==(curr.getClass())){
			return true;
		}return false;

	}

}


