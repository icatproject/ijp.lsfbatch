package org.icatproject.ijp.lsfbatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.icatproject.ijp.batch.exceptions.InternalException;

/**
 * Singleton class to manage sets of free/assigned Lsf user IDs
 * 
 * @author br54
 *
 */
public class LsfUserPool {
	
	private static LsfUserPool instance = null;
	protected LsfUserPool(){
		// Defeat instantiation (to some extent...)
		usersAssigned = new HashMap<String,Boolean>();
		familyPools = new HashMap<String,List<String>>();
	}
	public static LsfUserPool getInstance(){
		if( instance == null ){
			instance = new LsfUserPool();
		}
		return instance;
	}
	
	// Whether or not a user (name, String) is assigned
	private Map<String,Boolean> usersAssigned;
	
	// Map from family-name to list of user-names
	private Map<String,List<String>> familyPools;
	
	public List<String> addFamily(String family){
		List<String> userList = new ArrayList<>();
		familyPools.put(family, userList);
		return userList;
	}
	
	public void addLsfUser(String family, String user, boolean isAssigned){
	    if( usersAssigned.keySet().contains(user) ){
	        if( isAssigned ){
	          usersAssigned.put(user,true);
	        }
	      } else {
	        usersAssigned.put(user,isAssigned);
	      }
	      List<String> familyList = familyPools.get(family);
	      if( familyList == null ){
	        familyList = addFamily(family);
	      }
	      familyList.add(user);
	}
	
	public String getUserForFamily(String family) throws InternalException{
		List<String> familyPool = familyPools.get(family);
		if( familyPool == null ){
			throw new InternalException("Unrecognised family " + family);
		}
	    String user = null;
	    for( String familyUser : familyPool ){
	      if( ! usersAssigned.get(familyUser) ){
	        user = familyUser;
	        break;
	      }
	    }
	    if( user == null ){
	      throw new InternalException( "No free LSF users at present" );
	    }
	    usersAssigned.put(user,true);
		return user;
	}
	
	public void freeUser( String id ){
		usersAssigned.put(id, false);
	}
	
	public void clear(){
		familyPools = new HashMap<String,List<String>>();
		usersAssigned = new HashMap<String,Boolean>();
	}

}
