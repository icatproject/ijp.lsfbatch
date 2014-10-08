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
		familyPools = new HashMap<String,FamilyPool>();
	}
	public static LsfUserPool getInstance(){
		if( instance == null ){
			instance = new LsfUserPool();
		}
		return instance;
	}
	
	// We'll want one pair of free/assigned sets for each FamilyPool
	
	public class FamilyPool {
		
		List<String> free;
		List<String> assigned;
		
		public FamilyPool(){
			free = new ArrayList<String>();
			assigned = new ArrayList<String>();
		}
		
		public void add(String id){
			free.add(id);
		}
		
		public void addAssigned(String id){
			assigned.add(id);
		}
		
		public String getUser() throws InternalException{
			String id;
			if( free.isEmpty() ){
				throw new InternalException( "no free LSF users at present" );
			} else {
				id = free.get(0);
				free.remove(id);
				assigned.add(id);
			}
			return id;
		}
		
		public void freeUser( String id ){
			if( assigned.remove(id) ){
				free.add(id);
			}
		}
	}
	
	private Map<String,FamilyPool> familyPools;
	
	public FamilyPool addFamily(String family){
		FamilyPool familyPool = new FamilyPool();
		familyPools.put(family, familyPool);
		return familyPool;
	}
	
	public void addLsfUser(String family, String id, boolean isAssigned){
		FamilyPool familyPool = familyPools.get(family);
		if( familyPool == null ){
			familyPool = this.addFamily(family);
		}
		if( isAssigned ){
			familyPool.addAssigned(id);
		} else {
			familyPool.add(id);
		}
	}
	
	public String getUserForFamily(String family) throws InternalException{
		FamilyPool familyPool = familyPools.get(family);
		if( familyPool == null ){
			throw new InternalException("Unrecognised family " + family);
		}
		return familyPool.getUser();
	}
	
	public void freeUser( String family, String id ) throws InternalException{
		FamilyPool familyPool = familyPools.get(family);
		if( familyPool == null ){
			throw new InternalException("Unrecognised family " + family);
		}
		familyPool.freeUser(id);
	}
	
	public void freeUser( String id ){
		for( FamilyPool familyPool : familyPools.values() ){
			// freeUser() does nothing if the FamilyPool does not have that user (or if it's already free)
			familyPool.freeUser(id);
		}
	}

}
