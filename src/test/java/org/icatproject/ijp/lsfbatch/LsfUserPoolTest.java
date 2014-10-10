/**
 * 
 */
package org.icatproject.ijp.lsfbatch;

import static org.junit.Assert.*;

import org.icatproject.ijp.batch.exceptions.InternalException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * JUnit tests for the LsfUserPool class.
 * @author br54
 *
 */
public class LsfUserPoolTest {

	LsfUserPool userPool;
	
	@Before
	public void setUp(){
		userPool = LsfUserPool.getInstance();
	}
	
	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
		// LsfUserPool is a singleton, so let's make sure the test users/families are removed afterwards
		userPool.clear();
	}

	/**
	 * A few basic tests: that users are actually added to families, that getting them does remove them from the "free pool",
	 * that freeing them releases them back to the free pool, and that adding an already-assigned user works as expected.
	 */
	@Test
	public void basicTests() {
		
		userPool.addFamily("family1");
		userPool.addLsfUser("family1", "user1", false);
		
		try {
			assertEquals("Should get user1 for family1", userPool.getUserForFamily("family1"), "user1" );
		} catch (InternalException e) {
			fail("Get user for family1 threw exception: " + e.getMessage() );
		}
		
		// family1 should now have no free users
		try {
			userPool.getUserForFamily("family1");
			fail("family1 should have no free users, exception expected");
		} catch (InternalException e) {
			assertEquals("Get user for family1 should throw expected exception", e.getMessage(), "No free LSF users at present" );
		}
		
		userPool.freeUser("user1");
		try {
			assertEquals("user1 should be free again", userPool.getUserForFamily("family1"), "user1" );
		} catch (InternalException e) {
			fail("Get user for family1 threw exception: " + e.getMessage() );
		}
		
		// Add an already-assigned user
		userPool.addLsfUser("family1", "user2", true);
		
		// family1 should now have no free users (still)
		try {
			userPool.getUserForFamily("family1");
			fail("family1 should have no free users, exception expected");
		} catch (InternalException e) {
			assertEquals("Get user for family1 should throw expected exception", e.getMessage(), "No free LSF users at present" );
		}
		
		userPool.freeUser("user2");
		try {
			assertEquals("user2 should be free", userPool.getUserForFamily("family1"), "user2" );
		} catch (InternalException e) {
			fail("Get user for family1 threw exception: " + e.getMessage() );
		}
		
		userPool.freeUser("user1");
		userPool.freeUser("user2");
		
		// Add a third, assigned user. We should not get this back from getUserForFamily
		
		userPool.addLsfUser("family1", "user3", true);
		
		// Now get one of the free users
		
		try {
			String user = userPool.getUserForFamily("family1");
			assertTrue("User should be either user1 or user2", "user1".equals(user) || "user2".equals(user));
		} catch (InternalException e) {
			fail("Get user for family1 threw exception: " + e.getMessage() );
		}
		
		// ... and get the other one
		
		try {
			String user = userPool.getUserForFamily("family1");
			assertTrue("User should be either user1 or user2", "user1".equals(user) || "user2".equals(user));
		} catch (InternalException e) {
			fail("Get user for family1 threw exception: " + e.getMessage() );
		}
		
		// All users should now be assigned, so can't get any more
		
		try {
			userPool.getUserForFamily("family1");
			fail("family1 should have no free users, exception expected");
		} catch (InternalException e) {
			assertEquals("Get user for family1 should throw expected exception", e.getMessage(), "No free LSF users at present" );
		}
		
	}
	
	/**
	 * When a user is shared by two or more families, getting it from one family should mean that it is
	 * no longer available in the other families; and freeing it should make it available in each family
	 * again.
	 */
	@Test
	public void sharedUsersShouldWork(){
		
		// Add userA to two families
		// (This also tests that addLsfUser with a new family will actually add the family.)
		
		userPool.addLsfUser("family1", "userA", false);
		userPool.addLsfUser("family2", "userA", false);
		
		try {
			String user = userPool.getUserForFamily("family1");
			assertEquals("Should get userA for family1", user, "userA" );
			
		} catch (InternalException e) {
			fail("Get user for family1 threw exception: " + e.getMessage() );
		}
		
		// Now family2 should have no free users, as userA should be assigned
		
		try {
			userPool.getUserForFamily("family2");
			fail("family2 should have no free users, exception expected");
		} catch (InternalException e) {
			assertEquals("Get user for family2 should throw expected exception", e.getMessage(), "No free LSF users at present" );
		}
		
		userPool.freeUser("userA");
		
		try {
			String user = userPool.getUserForFamily("family2");
			assertEquals("Should get userA for family2", user, "userA" );
			
		} catch (InternalException e) {
			fail("Get user for family2 threw exception: " + e.getMessage() );
		}
		
		
		try {
			userPool.getUserForFamily("family1");
			fail("family1 should have no free users, exception expected");
		} catch (InternalException e) {
			assertEquals("Get user for family1 should throw expected exception", e.getMessage(), "No free LSF users at present" );
		}
	}

}
