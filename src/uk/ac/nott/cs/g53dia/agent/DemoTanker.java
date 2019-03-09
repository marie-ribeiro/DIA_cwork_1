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
	HashMap <Integer,Coords> allStationsMap = new HashMap<>();
	HashMap <Integer,Coords> allWellsMap = new HashMap<>();
	HashMap <Integer,Coords> allPumpsMap = new HashMap<>();

	int direction =0;
	Boolean goalSet = false; Coords goalCoords= null; String goalClass= null; Boolean gotFuel = false; Point nearestUtilityPoint;
	Boolean resetVars = false;
	Boolean arrived = null;

	int currStepCount = 0;
	Boolean stationTask = false; Boolean wellTask = false;
	Coords activeStation = null; Coords wellFound = null; int sameStationTask=0;
	int stationHashId=0, wellHashId=0, pumpHashId=0;
	double fuelThreshold; double wasteThreshold;
	static final String EXECUTE_GOAL_REFUEL = "Action_Refuel";
	static final String MOVE_DIR_REFUEL = "MoveTowards_Refuel";
	static final String MOVE_DIR_STATION = "MoveTowards_Station";
	static final String MOVE_DIR_WELL = "MoveTowards_Well";
	static final String EXECUTE_GOAL_LOAD_WASTE = "Action_getWasteTask";
	static final String EXECUTE_GOAL_DISPOSE_WASTE = "Action_disposeWaste";
	
	int tankerDist=0; int tanker_xDist=0; int tanker_yDist=0;
	int attemptswithCurrentFuelCap=0;
	
	public Action senseAndAct(Cell[][] view, boolean actionFailed, long timestep) {
		wasteThreshold = MAX_WASTE * 70.0/100.0; //max waste level before need for disposing waste
		
		updateState(view); //update the agent's map of the environment

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
			System.out.println("GoalName: "+goal.name+ "TowardsX: "+goal.coords.x+ "TowardsY: "+goal.coords.y);
			currStepCount++;
			//return new MoveTowardsAction(goal.point);
			return aMoveAction(goal, view);
			//return aMoveAction(new Goal(null, null, "Wander"), view);
		}


		//wander if no goal
		currStepCount++;
		//return new MoveAction(direction);
		return aMoveAction(new Goal(null, null, "Wander"), view);
	}

	public LoadWasteAction loadWasteAction(Cell[][]view) {
		System.out.println("Waste Loaded!");
		Station st = (Station)getCurrentCell(view);
		Task task = st.getTask();
		return new LoadWasteAction(task);

	}

	public Goal formulateGoal(Cell[][]view) {
		Goal goal = null; Coords nearestPump;

		//System.out.println("2xstepCount"+(2*currStepCount)+"fuelCapacity"+(getFuelLevel()-2));
		//FUEL CHECK
		Coords currentCoords= currentCoords(view);
		int tankerX = currentCoords.x-20+tanker_xDist; 
		int tankerY = 20-currentCoords.y+tanker_yDist;
		int nearestFuelpumpDist = 0; Boolean defaultPump = false;
		
		//recalculate distance to nearest fuelpump
		nearestPump = nearestPumporWell(allPumpsMap, view);
		if(nearestPump == null || 
				Math.max(Math.abs(tankerX),Math.abs(tankerY))<Math.max(Math.abs(nearestPump.x-tankerX), Math.abs(nearestPump.y-tankerY))) {//no near pump or nearest pump too far set to dewfault pump
			nearestFuelpumpDist = Math.max(tankerX, tankerY);
			defaultPump = true;
		}else {
			nearestFuelpumpDist = Math.max(Math.abs(nearestPump.x - tankerX), Math.abs(nearestPump.y - tankerY));
			defaultPump = false;
		}
		System.out.println(" nearest pump:"+nearestFuelpumpDist*2+ "this fuelminusf"+(this.getFuelLevel()-4));

		//if(this.getFuelLevel()-4 <= 2*currStepCount && !(getCurrentCell(view) instanceof FuelPump)) {
		if(this.getFuelLevel()-4 <= nearestFuelpumpDist*2 && !(getCurrentCell(view) instanceof FuelPump)) {	
			System.out.println("going back for fuel");
			if(defaultPump) {
				return new Goal(FUEL_PUMP_LOCATION, new Coords(0,0, FUEL_PUMP_LOCATION,Math.max(tankerX, tankerY), FuelPump.class.getName()), MOVE_DIR_REFUEL);
			}else {
				return new Goal(nearestPump.point, nearestPump, MOVE_DIR_REFUEL);
			}
		}
		
		if(getCurrentCell(view) instanceof FuelPump && this.getFuelLevel()<this.MAX_FUEL) {
			direction = (direction+1) %8;
			if(stationTask) {sameStationTask++;} //DEAL WITH SAME STATION MULTIPLE ATTEMPTS
			return new Goal(getCurrentCell(view).getPoint(),null, EXECUTE_GOAL_REFUEL);
		}
		//End of fuel check
		
		if(attemptswithCurrentFuelCap>=3) {
			System.out.println("More than 3 attempts to find stations with right amount of waste for current capacity");
			stationTask = false;
			wellTask = true;
			attemptswithCurrentFuelCap = 0;
		}
		if(sameStationTask >=3) {
			System.out.println("same station make changes");
			stationTask = false;
			direction = (direction+1) %8;//new trying to get it to stop hovering
			sameStationTask = 0;
		}
		
		//WASTE CAPACITY CHECK
		wellFound = nearestPumporWell(allWellsMap, view);
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
			Coords tempStation= findActiveStations(allStationsMap, view);//will return nearest currently seen active station
			if(tempStation!=null) {
				if(distanceToUtility(tempStation, view)<distanceToUtility(activeStation, view)) {
					System.out.println("Swap station");
					activeStation = tempStation;
				}	
				Station st =  (Station)view[activeStation.x+20-tanker_xDist][-(activeStation.y-20-tanker_yDist)];
				if(st.getTask().getWasteRemaining()>this.getWasteCapacity() && wellFound!=null) {
					wellTask = true; stationTask = false;
					return new Goal(wellFound.point, wellFound, MOVE_DIR_WELL);
				}else {wellTask = false;}
			}
			return new Goal(activeStation.point,activeStation, MOVE_DIR_STATION);
		}
		//needs new task 
		activeStation = findActiveStations(allStationsMap, view);//find nearest station we can currently see
		if(activeStation!=null && !(getCurrentCell(view)instanceof Station)) {//check for nearby station with task and set as goal
			System.out.println("FOUND active STATION!!!!!!!!!!!!!!!!!!!"+ activeStation.x+"y:"+activeStation.y);
			stationTask = true;
			
			Station st =  (Station)view[activeStation.x+20-tanker_xDist][-(activeStation.y-20-tanker_yDist)];
			if(st.getTask().getWasteRemaining()>this.getWasteCapacity() && wellFound!=null) {
				wellTask = true; stationTask = false;
				return new Goal(wellFound.point, wellFound, MOVE_DIR_WELL);
			}else {wellTask = false;}
			
			return new Goal(activeStation.point,activeStation, MOVE_DIR_STATION);
		}
		if(getCurrentCell(view) instanceof Station ) {
			direction = (direction+1) %8;
			Station st = (Station)getCurrentCell(view);
			if(st.getTask()!=null) { //&& st.getTask().getWasteRemaining()<this.getWasteCapacity()) {
				stationTask = false; activeStation = null;
				attemptswithCurrentFuelCap=0;
				sameStationTask=0;
				System.out.println("LOADING WASTE" );
				return new Goal(getCurrentCell(view).getPoint(),null,EXECUTE_GOAL_LOAD_WASTE);
			}else if(st.getTask()!=null && st.getTask().getWasteRemaining()>this.getWasteCapacity()) {
				System.out.println("too much waste here");
				attemptswithCurrentFuelCap++;
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
		//Updates the state for the immediate environment, storing all utilities found. Also stores utilities seen preiviously in their unique hashmaps so 
		//the agent can thus build up on its perceived environment
		Coords currCoords = currentCoords(view);
				
		for(int i=0; i<view.length; i++) {
			for(int j=0; j<view.length; j++) {
				int[] xyDisp = new int[2];
				int x= i-20+tanker_xDist; int y = 20-j+tanker_yDist;
				xyDisp = xyDisplacement(tanker_xDist, tanker_yDist, x, y);
				if(view[i][j].getClass()==(Station.class)) {//store station location in it's hashmap if found
					Coords newUtility = new Coords(x,y,view[i][j].getPoint(),Math.max(xyDisp[0], xyDisp[1]), Station.class.getName());
					if(!isUtilitySavedAlready(newUtility, allStationsMap)) {
						allStationsMap.put( stationHashId, newUtility ) ;
						stationHashId++;
					}else {
						//update station and thus task
						updateStationUtility(newUtility, allStationsMap);
					}
				}else if(view[i][j].getClass().equals(Well.class)){//store well location in it's hashmap if found
					Coords newUtility = new Coords(x,y,view[i][j].getPoint(),Math.max(xyDisp[0], xyDisp[1]), Well.class.getName());
					if(!isUtilitySavedAlready(newUtility, allWellsMap)) {
						allWellsMap.put( wellHashId, newUtility );
						wellHashId++;
					}
				}else if(view[i][j].getClass().equals(FuelPump.class)) {//store pump location in it's hashmap if found
					Coords newUtility = new Coords(x,y,view[i][j].getPoint(),Math.max(xyDisp[0], xyDisp[1]), FuelPump.class.getName());
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
		Coords utilityCoords;
		int minDistance=30000; //
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
			return new MoveAction(direction);
		}

		int currX = tanker_xDist;//currentCoords(view).x;
		int currY = tanker_yDist;//currentCoords(view).y;
		int dir = 0;
		int targetX = g.coords.x;//g.coords.x-20+tanker_xDist;
		int targetY = g.coords.y;//20-g.coords.y+tanker_yDist;
		System.out.println("Destination:"+g.name+" " +"x: "+g.coords.x+"y:"+g.coords.y);
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
		//System.out.println("tankDist:"+ tankerDist+" xdist:"+tanker_xDist+" yDist: "+tanker_yDist);

		return new MoveAction(dir);
	}

	public Boolean atLocation(String className, Cell curr) throws ClassNotFoundException {
		if(Class.forName(className)==(curr.getClass())){
			return true;
		}return false;

	}

}


