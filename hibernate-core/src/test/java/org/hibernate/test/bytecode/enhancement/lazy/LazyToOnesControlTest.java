/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.test.bytecode.enhancement.lazy;

import org.hibernate.Hibernate;
import org.hibernate.LazyInitializationException;
import org.hibernate.Session;
import org.hibernate.annotations.*;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.SessionFactoryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.graph.GraphParser;
import org.hibernate.graph.RootGraph;
import org.hibernate.stat.Statistics;
import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.bytecode.enhancement.BytecodeEnhancerRunner;
import org.hibernate.testing.junit4.BaseNonConfigCoreFunctionalTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.persistence.Table;
import javax.persistence.*;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

import static org.hibernate.testing.transaction.TransactionUtil.doInHibernate;
import static org.junit.Assert.*;


@TestForIssue( jiraKey = "HHH-14500" )
@RunWith(BytecodeEnhancerRunner.class)
public class LazyToOnesControlTest extends BaseNonConfigCoreFunctionalTestCase {

	private static final ArrayList<Field> TEST_FIELDS = new ArrayList<>(
			Arrays.stream(TestEntity.class.getDeclaredFields())
					.filter(f -> f.getName().startsWith("link"))
					.sorted(Comparator.comparing(Field::getName))
					.collect(Collectors.toList()));

	@Override
	protected void configureStandardServiceRegistryBuilder(StandardServiceRegistryBuilder ssrb) {
		super.configureStandardServiceRegistryBuilder( ssrb );
		ssrb.applySetting( AvailableSettings.ALLOW_ENHANCEMENT_AS_PROXY, "false" );
	}

	@Override
	protected void configureSessionFactoryBuilder(SessionFactoryBuilder sfb) {
		super.configureSessionFactoryBuilder( sfb );
		sfb.applyStatisticsSupport( true );
		sfb.applySecondLevelCacheSupport( false );
		sfb.applyQueryCacheSupport( false );
	}

	@Override
	protected void applyMetadataSources(MetadataSources sources) {
		super.applyMetadataSources( sources );
		sources.addAnnotatedClass( TestEntity.class );
	}

	@Before
	public void setupData() {
		inTransaction(
				session -> {
					final ArrayList<TestEntity> testEntities = new ArrayList<>(25);

					testEntities.add(persisted(session,  1, "link"));
					testEntities.add(persisted(session,  2, "linkSelect"));
					testEntities.add(persisted(session,  3, "linkJoin"));
					testEntities.add(persisted(session,  4, "linkNoProxy"));
					testEntities.add(persisted(session,  5, "linkNoProxySelect"));
					testEntities.add(persisted(session,  6, "linkNoProxyJoin"));

					testEntities.add(persisted(session,  7, "linkLazy"));
					testEntities.add(persisted(session,  8, "linkLazySelect"));
					testEntities.add(persisted(session,  9, "linkLazyJoin"));
					testEntities.add(persisted(session, 10, "linkLazyNoProxy"));
					testEntities.add(persisted(session, 11, "linkLazyNoProxySelect"));
					testEntities.add(persisted(session, 12, "linkLazyNoProxyJoin"));

					testEntities.add(persisted(session, 50, "bare"));

					testEntities.add(persisted(session, 101, "shallow-fetchgraph"));
					testEntities.add(persisted(session, 102, "id-fetchgraph"));
					testEntities.add(persisted(session, 103, "id-name-fetchgraph"));
					testEntities.add(persisted(session, 104, "complete-fetchgraph"));

					testEntities.add(persisted(session, 111, "shallow-loadgraph"));
					testEntities.add(persisted(session, 112, "id-loadgraph"));
					testEntities.add(persisted(session, 113, "id-name-loadgraph"));
					testEntities.add(persisted(session, 114, "complete-loadgraph"));

					testEntities.add(persisted(session, 201, "shallow-fetch"));
					testEntities.add(persisted(session, 202, "id-fetch"));
					testEntities.add(persisted(session, 203, "id-name-fetch"));
					testEntities.add(persisted(session, 204, "complete-fetch"));

					for (final TestEntity entity: testEntities) {
						// Commented out but kept for completeness - these are not explicitly annotated as LAZY as per JPA
						// so technically they may not be as JPA defaults to EAGER for ...ToOnes.

						// entity.setLink(testEntities.get(0));
						// entity.setLinkSelect(testEntities.get(1));
						// entity.setLinkJoin(testEntities.get(2));
						// entity.setLinkNoProxy(testEntities.get(3));
						// entity.setLinkNoProxySelect(testEntities.get(4));
						// entity.setLinkNoProxyJoin(testEntities.get(5));

						entity.setLinkLazy(testEntities.get(6));
						entity.setLinkLazySelect(testEntities.get(7));
						entity.setLinkLazyJoin(testEntities.get(8));
						entity.setLinkLazyNoProxy(testEntities.get(9));
						entity.setLinkLazyNoProxySelect(testEntities.get(10));
						entity.setLinkLazyNoProxyJoin(testEntities.get(11));

						session.saveOrUpdate(entity);
					}
				}
		);
	}

	private TestEntity persisted(final Session session, final long id, final String name) {
		final TestEntity newEntity = new TestEntity();
		newEntity.setId(id);
		newEntity.setName(name);
		session.persist(newEntity);
		return newEntity;
	}

	@After
	public void cleanUpTestData() {
		inTransaction(
				session -> {
					session.createQuery( "delete from TestEntity" ).executeUpdate();
				}
		);
	}

	@Test
	public void testBare() {
		testLoading(50, 0, 0, null, false);
	}

	@Test
	public void testShallowFetchGraph() {
		testGraphLoading(101, 1, "javax.persistence.fetchgraph");
	}

	@Test
	public void testIdOnlyFetchGraph() {
		testGraphLoading(102, 2, "javax.persistence.fetchgraph");
	}

	@Test
	public void testIdAndNameFetchGraph() {
		testGraphLoading(103, 3, "javax.persistence.fetchgraph");
	}

	@Test
	public void testCompleteFetchGraph() {
		testGraphLoading(104, 4, "javax.persistence.fetchgraph");
	}

	@Test
	public void testShallowLoadGraph() {
		testGraphLoading(101, 1, "javax.persistence.loadgraph");
	}

	@Test
	public void testIdOnlyLoadGraph() {
		testGraphLoading(102, 2,"javax.persistence.loadgraph");
	}

	@Test
	public void testIdAndNameLoadGraph() {
		testGraphLoading(103, 3, "javax.persistence.loadgraph");
	}

	@Test
	public void testCompleteLoadGraph() {
		testGraphLoading(104, 4, "javax.persistence.loadgraph");
	}

	@Test
	public void testShallowFetch() {
		testFetchLoading(201, 1);
	}

	@Test
	public void testIdOnlyFetch() {
		testFetchLoading(202, 2);
	}

	@Test
	public void testIdAndNameFetch() {
		testFetchLoading(203, 3);
	}

	@Test
	public void testCompleteFetch() {
		testFetchLoading(204, 4);
	}

	private void testGraphLoading(final long id, int graphLevel, final String hintName) {
		testLoading(id, graphLevel, 0, hintName, false);
	}

	private void testFetchLoading(final long id, int fetchLevel) {
		testLoading(id, 0, fetchLevel, null, false);
	}

	private void testLoading(final long id, int graphLevel, int fetchLevel, final String hintName, final boolean tryAfterSessionClosed) {
		final TestEntity[] testEntity = new TestEntity[1];

		inSession(session -> {
			final CriteriaBuilder builder = session.getCriteriaBuilder();

			log("################################################################################");
			log(" TEST " + id + (graphLevel > 0 ? " graphLevel=" + graphLevel + "/" + hintName : " no graphLevel") + (fetchLevel > 0 ? " fetchLevel=" + fetchLevel : " no fetchLevel"));
			log("################################################################################");
			session.clear();
			final CriteriaQuery<TestEntity> query = builder.createQuery(TestEntity.class);
			final Root<TestEntity> root = query.from(TestEntity.class);
			final Path<Long> idPath = root.get("id");
			query.where(builder.equal(idPath, id));

			log("final CriteriaQuery<TestEntity> query = builder.createQuery(TestEntity.class);");
			log("final Root<TestEntity> root = query.from(TestEntity.class);");
			log("final Path<Long> id = root.get(\"id\");");
			log("query.where(builder.equal(id, " + id + "L));");

			specifyFetches(root, fetchLevel);

			log("final TypedQuery<TestEntity> typedQuery = session.createQuery(query);");
			final TypedQuery<TestEntity> typedQuery = session.createQuery(query);

			specifyGraph(typedQuery, graphLevel, hintName, session);

			log("final TestEntity test = typedQuery.getSingleResult();");

			final Statistics stats = sessionFactory().getStatistics();
			stats.clear();

			testEntity[0] = typedQuery.getSingleResult();

			final boolean allDataAvailableInSingleRow = (graphLevel < 3) && (fetchLevel < 3);

			// Consideration...
			// JPA default for ...ToOne properties is "eager", unfortunately.
			// If that is applied then some of the properties will be eagerly loaded
			// even though they do not explicitly specify EAGER fetching.
			// Either way is acceptable as long as we have a way of forcing lazy treatment
			// when graphs/fetches aren't specified and eager treatment when they are.

			if (allDataAvailableInSingleRow) {
				// We expect everything to be there already
				assertEquals( "Only a single SQL statement is executed", 1, stats.getPrepareStatementCount());
			} else {
				// We allow a base query + at most one extra to load the rest of the data
				assertTrue( "Up to two SQL statements are executed", stats.getPrepareStatementCount() <= 2);
			}

			stats.clear();

			log("test.reportInitializationState();");
			testEntity[0].reportInitializationState();

			assertEquals( 0, stats.getPrepareStatementCount());
			stats.clear();

			for (final Field field: TEST_FIELDS) {
				if (field.getName().contains("Lazy") && field.getName().contains("NoProxy")) {
					assertFalse(
							"Lazy fields without a proxy should not be initialized yet",
							Hibernate.isPropertyInitialized(testEntity[0], field.getName())
					);
				}
			}

			if (!tryAfterSessionClosed) {
				log("test.printAll(); // within session");
				testEntity[0].printAll();

				if ((graphLevel >= 4) || (fetchLevel >= 4)) {
					// Everything should already have been loaded.
					assertEquals("No additional SQL statements are needed or executed", 0, stats.getPrepareStatementCount());
				} else if ((graphLevel >= 3) || (fetchLevel >= 3)) {
					// As we don't access linked entities links there should have been no need for additional SQL
					assertEquals("No additional SQL statements are needed or executed", 0, stats.getPrepareStatementCount());
				} else {
					// We asked for additional properties. If no additional SQL was executed
					// that would mean that the data was eagerly loaded upfront, which is NOT what we wanted.
					assertTrue("Additional SQL is executed to get additional properties", stats.getPrepareStatementCount() > 0);

					// FIXME IMPORTANT NOTE:
					// Additional SQL query that is issued at the time of writing this test only gets ids.
					// Hibernate appears to not need the rest - that data was already loaded even though it
					// should not have been. I do not know how to check for that here.
				}
			}

			session.close();
		});

		if (tryAfterSessionClosed) {
			// Since we specified eager loading of everything we need then we should get no
			// lazy initialization exceptions even though the session is closed:
			log("test.printAll(); // after session");
			try {
				testEntity[0].printAll();
			} catch (final LazyInitializationException e) {
				fail("Must not throw " + e.getClass().getName() + " when reading properties of a sufficiently loaded entity.");
			}
			log("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
		} else if ((graphLevel >= 3) || (fetchLevel >= 3)) {
			// Since we specified eager loading of everything we need then we should get no
			// lazy initialization exceptions if we try this same thing after the session is closed:
			testLoading(id, graphLevel, fetchLevel, hintName, true);
		}
	}

	private void specifyGraph(TypedQuery<TestEntity> typedQuery, int graphLevel, String hintName, SessionImplementor session) {
		if (graphLevel > 0) {
			final StringBuilder source = new StringBuilder(1000);

			for (Field f: TestEntity.class.getDeclaredFields()) {
				String name = f.getName();
				if (name.startsWith("link")) {
					if (source.length() > 0) {
						source.append(", ");
					}
					source.append(name);
					switch (graphLevel) {
						case 1:
							break;

						case 2:
							source.append("(id)");
							break;

						case 3:
							source.append("(id, name)");
							break;

						case 4:
							source.append("(id, name");
							for (Field f2: TestEntity.class.getDeclaredFields()) {
								String name2 = f2.getName();
								if (name2.startsWith("link")) {
									source.append(", ");
									source.append(name2);
								}
							}
							source.append(')');
							break;
					}
				}
			}

			log("final String graphText = \"" + source.toString() + "\";");
			log("final RootGraph<TestEntity> entityGraph = GraphParser.parse(TestEntity.class, graphText, session);");

			final RootGraph<TestEntity> entityGraph =
					GraphParser.parse(TestEntity.class, source.toString(), session);

			log("typedQuery.setHint(\"" + hintName + "\", entityGraph);");
			typedQuery.setHint(hintName, entityGraph);
		}
	}

	private void specifyFetches(Root<TestEntity> root, int fetchLevel) {
		if (fetchLevel > 0) {
			for (Field f: TestEntity.class.getDeclaredFields()) {
				String name = f.getName();
				if (name.startsWith("link")) {
					final javax.persistence.criteria.Fetch<TestEntity, TestEntity> linkFetch = root.fetch(name);
					log("final Fetch<TestEntity, TestEntity> " + name + "Fetch = root.fetch(\"" + name + "\");");

					switch (fetchLevel) {
						case 1:
							break;

						case 2:
							//linkFetch.fetch("id"); // Basic properties are not to be fetched
							break;

						case 3:
							//linkFetch.fetch("id"); // Basic properties are not to be fetched
							//linkFetch.fetch("name"); // Basic properties are not to be fetched
							break;

						case 4:
							//linkFetch.fetch("id"); // Basic properties are not to be fetched
							//linkFetch.fetch("name"); // Basic properties are not to be fetched
							for (Field f2: TestEntity.class.getDeclaredFields()) {
								String name2 = f2.getName();
								if (name2.startsWith("link")) {
									linkFetch.fetch(name2);
									log(name + "Fetch.fetch(\"" + name2 + "\");");
								}
							}
							break;
					}
				}
			}
		}
	}

	public static void log(Object what) {
		System.err.flush();
		System.out.println("######### " + what);
		System.out.flush();
		System.err.flush();
	}


	@javax.persistence.Entity(name = "TestEntity")
	@Table(name = "loading_test")
	@org.hibernate.annotations.BatchSize(size = 100)
	@org.hibernate.annotations.DynamicInsert(false)
	@org.hibernate.annotations.DynamicUpdate(false)
	@org.hibernate.annotations.SelectBeforeUpdate(false)
	@org.hibernate.annotations.Proxy(lazy = false)
	@org.hibernate.annotations.OptimisticLocking(type = org.hibernate.annotations.OptimisticLockType.VERSION)
	@org.hibernate.annotations.Polymorphism(type = org.hibernate.annotations.PolymorphismType.IMPLICIT)
	@javax.persistence.Access(javax.persistence.AccessType.FIELD)
	@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
	public static class TestEntity {
		@Id
		@Column(name = "id", nullable = false, updatable = false)
		@org.hibernate.annotations.Type(type = "long")
		private Long id = null;

		@Version
		@Column(name = "version", nullable = false)
		@org.hibernate.annotations.Type(type = "int")
		private int version = 0;

		@Column(name = "name")
		@javax.persistence.Basic
		@org.hibernate.annotations.OptimisticLock(excluded = false)
		@org.hibernate.annotations.Type(type = "java.lang.String")
		private String name;

		// JPA defaults ...ToOne properties to EAGER so technically those could be treated as such.
		// For this reason they are commented out and kept in code for completeness.

		// @ManyToOne
		// @JoinColumn(name = "link_id", nullable = true)
		// @OptimisticLock(excluded = false)
		// @NotFound(action = NotFoundAction.IGNORE)
		// private TestEntity link;

		// @ManyToOne
		// @Fetch(FetchMode.SELECT)
		// @JoinColumn(name = "linkSelect_id", nullable = true)
		// @OptimisticLock(excluded = false)
		// @NotFound(action = NotFoundAction.IGNORE)
		// private TestEntity linkSelect;

		// @ManyToOne
		// @Fetch(FetchMode.JOIN)
		// @JoinColumn(name = "linkJoin_id", nullable = true)
		// @OptimisticLock(excluded = false)
		// @NotFound(action = NotFoundAction.IGNORE)
		// private TestEntity linkJoin;

		// @ManyToOne
		// @LazyToOne(LazyToOneOption.NO_PROXY)
		// @JoinColumn(name = "linkNoProxy_id", nullable = true)
		// @OptimisticLock(excluded = false)
		// @NotFound(action = NotFoundAction.IGNORE)
		// private TestEntity linkNoProxy;

		// @ManyToOne
		// @LazyToOne(LazyToOneOption.NO_PROXY)
		// @Fetch(FetchMode.SELECT)
		// @JoinColumn(name = "linkNoProxySelect_id", nullable = true)
		// @OptimisticLock(excluded = false)
		// @NotFound(action = NotFoundAction.IGNORE)
		// private TestEntity linkNoProxySelect;

		// @ManyToOne
		// @LazyToOne(LazyToOneOption.NO_PROXY)
		// @Fetch(FetchMode.JOIN)
		// @JoinColumn(name = "linkNoProxyJoin_id", nullable = true)
		// @OptimisticLock(excluded = false)
		// @NotFound(action = NotFoundAction.IGNORE)
		// private TestEntity linkNoProxyJoin;

		@ManyToOne(fetch = FetchType.LAZY)
		@JoinColumn(name = "linkLazy_id", nullable = true)
		@OptimisticLock(excluded = false)
		@NotFound(action = NotFoundAction.IGNORE)
		private TestEntity linkLazy;

		@ManyToOne(fetch = FetchType.LAZY)
		@Fetch(FetchMode.SELECT)
		@JoinColumn(name = "linkLazySelect_id", nullable = true)
		@OptimisticLock(excluded = false)
		@NotFound(action = NotFoundAction.IGNORE)
		private TestEntity linkLazySelect;

		@ManyToOne(fetch = FetchType.LAZY)
		@Fetch(FetchMode.JOIN)
		@JoinColumn(name = "linkLazyJoin_id", nullable = true)
		@OptimisticLock(excluded = false)
		@NotFound(action = NotFoundAction.IGNORE)
		private TestEntity linkLazyJoin;

		@ManyToOne(fetch = FetchType.LAZY)
		@LazyToOne(LazyToOneOption.NO_PROXY)
		@JoinColumn(name = "linkLazyNoProxy_id", nullable = true)
		@OptimisticLock(excluded = false)
		@NotFound(action = NotFoundAction.IGNORE)
		private TestEntity linkLazyNoProxy;

		@ManyToOne(fetch = FetchType.LAZY)
		@LazyToOne(LazyToOneOption.NO_PROXY)
		@Fetch(FetchMode.SELECT)
		@JoinColumn(name = "linkLazyNoProxySelect_id", nullable = true)
		@OptimisticLock(excluded = false)
		@NotFound(action = NotFoundAction.IGNORE)
		private TestEntity linkLazyNoProxySelect;

		@ManyToOne(fetch = FetchType.LAZY)
		@LazyToOne(LazyToOneOption.NO_PROXY)
		@Fetch(FetchMode.JOIN)
		@JoinColumn(name = "linkLazyNoProxyJoin_id", nullable = true)
		@OptimisticLock(excluded = false)
		@NotFound(action = NotFoundAction.IGNORE)
		private TestEntity linkLazyNoProxyJoin;

		// Test and results data
		public Long getId() {
			return id;
		}

		void setId(final long id) {
			this.id = id;
		}

		public int getVersion() {
			return version;
		}

		public String getName() {
			return name;
		}

		public void setName(final String name) {
			this.name = name;
		}

		// JPA defaults ...ToOne properties to EAGER so technically those could be treated as such.
		// For this reason they are commented out and kept in code for completeness.

		// public TestEntity getLink() {
		// 	return link;
		// }

		// public void setLink(final TestEntity link) {
		// 	this.link = link;
		// }

		// public TestEntity getLinkSelect() {
		// 	return linkSelect;
		// }

		// public void setLinkSelect(final TestEntity linkSelect) {
		// 	this.linkSelect = linkSelect;
		// }

		// public TestEntity getLinkJoin() {
		// 	return linkJoin;
		// }

		// public void setLinkJoin(final TestEntity linkJoin) {
		// 	this.linkJoin = linkJoin;
		// }

		// public TestEntity getLinkNoProxy() {
		// 	return linkNoProxy;
		// }
//
		// public void setLinkNoProxy(final TestEntity linkNoProxy) {
		// 	this.linkNoProxy = linkNoProxy;
		// }
//
		// public TestEntity getLinkNoProxySelect() {
		// 	return linkNoProxySelect;
		// }
//
		// public void setLinkNoProxySelect(final TestEntity linkNoProxySelect) {
		// 	this.linkNoProxySelect = linkNoProxySelect;
		// }
//
		// public TestEntity getLinkNoProxyJoin() {
		// 	return linkNoProxyJoin;
		// }
//
		// public void setLinkNoProxyJoin(final TestEntity linkNoProxyJoin) {
		// 	this.linkNoProxyJoin = linkNoProxyJoin;
		// }

		public TestEntity getLinkLazy() {
			return linkLazy;
		}

		public void setLinkLazy(final TestEntity linkLazy) {
			this.linkLazy = linkLazy;
		}

		public TestEntity getLinkLazySelect() {
			return linkLazySelect;
		}

		public void setLinkLazySelect(final TestEntity linkLazySelect) {
			this.linkLazySelect = linkLazySelect;
		}

		public TestEntity getLinkLazyJoin() {
			return linkLazyJoin;
		}

		public void setLinkLazyJoin(final TestEntity linkLazyJoin) {
			this.linkLazyJoin = linkLazyJoin;
		}

		public TestEntity getLinkLazyNoProxy() {
			return linkLazyNoProxy;
		}

		public void setLinkLazyNoProxy(final TestEntity linkLazyNoProxy) {
			this.linkLazyNoProxy = linkLazyNoProxy;
		}

		public TestEntity getLinkLazyNoProxySelect() {
			return linkLazyNoProxySelect;
		}

		public void setLinkLazyNoProxySelect(final TestEntity linkLazyNoProxySelect) {
			this.linkLazyNoProxySelect = linkLazyNoProxySelect;
		}

		public TestEntity getLinkLazyNoProxyJoin() {
			return linkLazyNoProxyJoin;
		}

		public void setLinkLazyNoProxyJoin(final TestEntity linkLazyNoProxyJoin) {
			this.linkLazyNoProxyJoin = linkLazyNoProxyJoin;
		}

		public void reportColumns() {
			for (Field field : this.getClass().getDeclaredFields()) {
				System.out.print('\t');
				System.out.print(field.getName());
			}
			System.out.println();
			System.out.flush();
		}

		private String pad(final Object o, int length) {
			final StringBuilder b = new StringBuilder(length);
			b.append(String.valueOf(o));
			while (b.length() < length) b.append(' ');
			return b.toString();
		}

		public void reportInitializationState() {
			log("================================================================================");
			log(toString() + " Initialization State");
			log("--------------------------------------------------------------------------------");
			for (Field field: TEST_FIELDS) {
				final boolean loaded = Hibernate.isPropertyInitialized(this, field.getName());
				log(pad(field.getName() +  ":", 25) + (loaded ? "YES" : "n/a"));
			}
			log("--------------------------------------------------------------------------------");
			log(toString() + " Reflection State");
			log("--------------------------------------------------------------------------------");
			for (Field field: TEST_FIELDS) {
				boolean loaded;
				try {
					loaded = field.get(this) != null;
				} catch (IllegalAccessException e) {
					e.printStackTrace();
					loaded = false;
				}
				log(pad(field.getName() +  ":", 25) + (loaded ? "HERE" : "null"));
			}
			log("================================================================================");
		}

		public String toString() {
			return "#" + id + ":" + name;
		}

		public void printAll() {
			log("================================================================================");
			log(toString());
			log("--------------------------------------------------------------------------------");
			// log("link                     = " + link);
			// log("linkSelect               = " + linkSelect);
			// log("linkJoin                 = " + linkJoin);
			// log("linkNoProxy              = " + linkNoProxy);
			// log("linkNoProxySelect        = " + linkNoProxySelect);
			// log("linkNoProxyJoin          = " + linkNoProxyJoin);
			log("linkLazy                 = " + linkLazy);
			log("linkLazySelect           = " + linkLazySelect);
			log("linkLazyJoin             = " + linkLazyJoin);
			log("linkLazyNoProxy          = " + linkLazyNoProxy);
			log("linkLazyNoProxySelect    = " + linkLazyNoProxySelect);
			log("linkLazyNoProxyJoin      = " + linkLazyNoProxyJoin);
			log("================================================================================");
		}
	}
}