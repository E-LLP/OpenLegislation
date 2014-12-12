package gov.nysenate.openleg.service.law.data;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import gov.nysenate.openleg.dao.law.LawDataDao;
import gov.nysenate.openleg.model.cache.CacheWarmEvent;
import gov.nysenate.openleg.model.law.*;
import gov.nysenate.openleg.model.cache.CacheEvictEvent;
import gov.nysenate.openleg.model.cache.ContentCache;
import gov.nysenate.openleg.service.base.data.CachingService;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.MemoryUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.ehcache.EhCacheCache;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * Service interface for retrieving and saving NYS Law data.
 */
@Service
public class CachedLawDataService implements LawDataService, CachingService
{
    private static final Logger logger = LoggerFactory.getLogger(CachedLawDataService.class);

    @Autowired private LawDataDao lawDataDao;
    @Autowired private CacheManager cacheManager;
    @Autowired EventBus eventBus;

    @Value("${law.cache.size}") private long lawTreeCacheHeapSize;

    private static final String lawTreeCacheName = "lawtree";
    private EhCacheCache lawTreeCache;

    @PostConstruct
    private void init() {
        eventBus.register(this);
        setupCaches();
    }

    @PreDestroy
    private void cleanUp() {
        evictCaches();
        cacheManager.removeCache(lawTreeCacheName);
    }

    /** --- CachingService implementation --- */

    /** {@inheritDoc} */
    @Override
    public List<Ehcache> getCaches() {
        return Arrays.asList(lawTreeCache.getNativeCache());
    }

    /** {@inheritDoc} */
    @Override
    public void setupCaches() {
        Cache cache = new Cache(new CacheConfiguration().name(lawTreeCacheName)
                .eternal(true)
                .maxBytesLocalHeap(lawTreeCacheHeapSize, MemoryUnit.MEGABYTES)
                .sizeOfPolicy(defaultSizeOfPolicy()));
        cacheManager.addCache(cache);
        this.lawTreeCache = new EhCacheCache(cache);
    }

    /** {@inheritDoc} */
    @Override
    @Subscribe
    public void handleCacheEvictEvent(CacheEvictEvent evictEvent) {
        if (evictEvent.affects(ContentCache.LAW)) {
            evictCaches();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void warmCaches() {
        try {
            logger.info("Warming up law cache..");
            getLawInfos().forEach(lawInfo -> getLawTree(lawInfo.getLawId(), LocalDate.now()));
            logger.info("Finished warming up law cache..");
        }
        catch (LawTreeNotFoundEx ex) {
            logger.warn("Failed to warm up law cache!.", ex);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void handleCacheWarmEvent(CacheWarmEvent warmEvent) {
        if (warmEvent.affects(ContentCache.LAW)) {
            warmCaches();
        }
    }

    /** --- LawDataService implementation --- */

    /** {@inheritDoc} */
    @Override
    public List<LawInfo> getLawInfos() {
        List<LawInfo> infos = lawDataDao.getLawInfos();
        return infos.stream().sorted().collect(toList());
    }

    /** {@inheritDoc} */
    @Override
    public LawTree getLawTree(String lawId, LocalDate endPublishedDate) throws LawTreeNotFoundEx {
        if (lawId == null) throw new IllegalArgumentException("Supplied lawId cannot be null");
        if (endPublishedDate == null) endPublishedDate = LocalDate.now();
        try {
            LawVersionId lawVersionId = new LawVersionId(lawId, endPublishedDate);
            LawTree lawTree;
            if (lawTreeCache.get(lawVersionId) != null) {
                lawTree = (LawTree) lawTreeCache.get(lawVersionId).get();
            }
            else {
                lawTree = lawDataDao.getLawTree(lawId, endPublishedDate);
            }
            lawTreeCache.put(lawTree.getLawVersionId(), lawTree);
            return lawTree;
        }
        catch (DataAccessException ex) {
            throw new LawTreeNotFoundEx(lawId, endPublishedDate, ex.getMessage());
        }
    }

    /** {@inheritDoc} */
    @Override
    public LawDocument getLawDocument(String documentId, LocalDate endPublishedDate) throws LawDocumentNotFoundEx {
        if (documentId == null) throw new IllegalArgumentException("Supplied documentId cannot be null");
        if (endPublishedDate == null) endPublishedDate = LocalDate.now();
        try {
            return lawDataDao.getLawDocument(documentId, endPublishedDate);
        }
        catch (EmptyResultDataAccessException ex) {
            throw new LawDocumentNotFoundEx(documentId, endPublishedDate, "");
        }
    }

    /** {@inheritDoc} */
    @Override
    public void saveLawTree(LawFile lawFile, LawTree lawTree) {
        if (lawTree == null) throw new IllegalArgumentException("Supplied lawTree cannot be null");
        lawDataDao.updateLawTree(lawFile, lawTree);
        lawTreeCache.put(lawTree.getLawVersionId(), lawTree);
    }

    /** {@inheritDoc} */
    @Override
    public void saveLawDocument(LawFile lawFile, LawDocument lawDocument) {
        if (lawDocument == null) throw new IllegalArgumentException("Supplied lawDocument cannot be null");
        if (lawFile == null) throw new IllegalArgumentException("Supplied lawFile cannot be null");
        lawDataDao.updateLawDocument(lawFile, lawDocument);
    }
}