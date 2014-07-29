package gov.nysenate.openleg.service.sobi;

import gov.nysenate.openleg.model.agenda.Agenda;
import gov.nysenate.openleg.model.agenda.AgendaId;
import gov.nysenate.openleg.model.bill.BaseBillId;
import gov.nysenate.openleg.model.bill.Bill;
import gov.nysenate.openleg.model.bill.BillAmendment;
import gov.nysenate.openleg.model.bill.BillId;
import gov.nysenate.openleg.model.calendar.Calendar;
import gov.nysenate.openleg.model.calendar.CalendarId;
import gov.nysenate.openleg.model.entity.Chamber;
import gov.nysenate.openleg.model.entity.Member;
import gov.nysenate.openleg.model.sobi.SobiBlock;
import gov.nysenate.openleg.model.sobi.SobiFragment;
import gov.nysenate.openleg.service.agenda.AgendaDataService;
import gov.nysenate.openleg.service.agenda.AgendaNotFoundEx;
import gov.nysenate.openleg.service.base.IngestCache;
import gov.nysenate.openleg.service.bill.BillDataService;
import gov.nysenate.openleg.service.bill.BillNotFoundEx;
import gov.nysenate.openleg.service.calendar.CalendarDataService;
import gov.nysenate.openleg.service.calendar.CalendarNotFoundEx;
import gov.nysenate.openleg.service.entity.CommitteeService;
import gov.nysenate.openleg.service.entity.MemberNotFoundEx;
import gov.nysenate.openleg.service.entity.MemberService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;

/**
 * The AbstractSobiProcessor class is intended to serve as a common base for all the
 * Sobi file processors and provides functionality to fetch and persist various
 * entity types. This is to allow different processors to be consistent in how they
 * utilize various data operations.
 */
public abstract class AbstractSobiProcessor
{
    private static final Logger logger = LoggerFactory.getLogger(AbstractSobiProcessor.class);

    // TODO: Move this class out of here
    public static class ParseError extends Exception
    {
        private static final long serialVersionUID = 2809768377369235106L;

        public ParseError(String message) { super(message); }
    }

    /** --- Data Services --- */

    @Autowired
    protected BillDataService billDataService;
    @Autowired
    protected CalendarDataService calendarDataService;
    @Autowired
    protected AgendaDataService agendaDataService;
    @Autowired
    protected MemberService memberService;
    @Autowired
    protected CommitteeService committeeService;

    /** --- Bill Methods --- */

    /**
     * Retrieves/creates the Bill without checking a cache.
     *
     * @param publishDate Date
     * @param billId BillId
     * @return Bill
     */
    protected Bill getOrCreateBaseBill(Date publishDate, BillId billId) {
        return getOrCreateBaseBill(publishDate, billId, null);
    }

    /**
     * Retrieves/creates the Bill using the print no in the SobiBlock.
     *
     * @param fragment SobiFragment
     * @param block SobiBlock
     * @param billIngestCache IngestCache<Bill>
     * @return Bill
     */
    protected Bill getOrCreateBaseBill(SobiFragment fragment, SobiBlock block, IngestCache<BillId, Bill> billIngestCache) {
        return getOrCreateBaseBill(fragment.getPublishedDateTime(), block.getBillId(), billIngestCache);
    }

    /**
     * Retrieves the base Bill container using the given billId from either the cache or the service layer.
     * If this base bill does not exist, it will be created. The amendment instance will also be created
     * if it does not exist.
     *
     * @param publishDate Date - Typically the date of the source data file. Only used when bill information
     *                           does not already exist and must be created.
     * @param billId BillId - The BillId to find a matching Bill for.
     * @param billIngestCache IngestCache<BillId, Bill> - Optional cache used to speed up processing.
     * @return Bill
     */
    protected Bill getOrCreateBaseBill(Date publishDate, BillId billId, IngestCache<BillId, Bill> billIngestCache) {
        boolean isBaseVersion = BillId.isBaseVersion(billId.getVersion());
        BaseBillId baseBillId = BillId.getBaseId(billId);
        Bill baseBill;
        try {
            baseBill = getBaseBillFromCacheOrService(baseBillId, billIngestCache);
        }
        catch (BillNotFoundEx ex) {
            // Create the bill since it does not exist and add it to the ingest cache.
            if (!isBaseVersion) {
                logger.warn("Bill Amendment {} filed without initial bill.", billId);
            }
            baseBill = new Bill(baseBillId);
            baseBill.setModifiedDate(publishDate);
            baseBill.setPublishDate(publishDate);
            if (billIngestCache != null) {
                billIngestCache.set(baseBillId, baseBill);
            }
        }
        if (!baseBill.hasAmendment(billId.getVersion())) {
            BillAmendment billAmendment = new BillAmendment(baseBillId, billId.getVersion());
            billAmendment.setModifiedDate(publishDate);
            // If an active amendment exists, apply its ACT TO clause to this amendment
            if (baseBill.hasActiveAmendment()) {
                billAmendment.setActClause(baseBill.getActiveAmendment().getActClause());
            }
            // Create the base version if an amendment was received before the base version
            if (!isBaseVersion) {
                if (!baseBill.hasAmendment(BillId.BASE_VERSION)) {
                    BillAmendment baseAmendment = new BillAmendment(baseBillId, BillId.BASE_VERSION);
                    baseAmendment.setModifiedDate(publishDate);
                    baseBill.addAmendment(baseAmendment);
                    baseBill.setActiveVersion(BillId.BASE_VERSION);
                }
                // Pull 'shared' data from the currently active amendment
                BillAmendment activeAmendment = baseBill.getAmendment(baseBill.getActiveVersion());
                billAmendment.setCoSponsors(activeAmendment.getCoSponsors());
                billAmendment.setMultiSponsors(activeAmendment.getMultiSponsors());
            }
            logger.trace("Adding bill amendment: " + billAmendment);
            baseBill.addAmendment(billAmendment);
        }
        return baseBill;
    }

    /**
     * Helper method to retrieve Bill references from either the cache or the BillDataService.
     *
     * @param billId BillId
     * @param billIngestCache IngestCache<BillId, Bill> (can be null)
     * @return Bill
     * @throws BillNotFoundEx - If the bill was not found by the service.
     */
    protected Bill getBaseBillFromCacheOrService(BillId billId, IngestCache<BillId, Bill> billIngestCache) throws BillNotFoundEx {
        if (billId == null) {
            throw new IllegalArgumentException("Bill Id cannot be null!");
        }
        // Ensure bill id references the base bill id since the cache will not distinguish.
        BaseBillId baseBillId = BillId.getBaseId(billId);
        // Try the cache, otherwise use the service which can throw the BillNotFoundEx exception.
        boolean isCached = (billIngestCache != null) && billIngestCache.has(baseBillId);
        logger.trace("Bill ingest cache " + ((isCached) ? "HIT" : "MISS") + " for bill id " + baseBillId);
        return (isCached) ? billIngestCache.get(baseBillId) : billDataService.getBill(baseBillId);
    }

    /** --- Member Methods --- */

    /**
     * Retrieves a member from the LBDC short name with special processing if the member was not found
     * and required is true. Returns null if required is false and member is not found.
     *
     * @param shortName String
     * @param sessionYear int
     * @param chamber Chamber
     * @param required boolean
     * @return Member
     */
    protected Member getMemberFromShortName(String shortName, int sessionYear, Chamber chamber, boolean required) {
        if (StringUtils.isNotBlank(shortName)) {
            try {
                return memberService.getMemberByLBDCName(shortName, sessionYear, chamber);
            }
            catch (MemberNotFoundEx memberNotFoundEx) {
                logger.error("", memberNotFoundEx);
                if (required) {
                    System.exit(-1); /** FIXME */
                }
            }
        }
        return null;
    }

    /** --- Agenda Methods --- */

    /**
     * Retrieve an Agenda instance from the backing store or create it if it does not exist.
     *
     * @param agendaId AgendaId - Retrieve Agenda via this agendaId.
     * @param date Date - The published date of the requesting sobi fragment.
     * @return Agenda
     */
    protected Agenda getOrCreateAgenda(AgendaId agendaId, Date date) {
        Agenda agenda;
        try {
            agenda = agendaDataService.getAgenda(agendaId);
        }
        catch (AgendaNotFoundEx ex) {
            agenda = new Agenda(agendaId);
            agenda.setModifiedDate(date);
            agenda.setPublishDate(date);
        }
        return agenda;
    }

    /** --- Calendar Methods --- */

    /**
     * Retrieve a Calendar from the backing store or create it if it does not exist.
     *
     * @param calendarId CalendarId - Retrieve Calendar via this calendarId.
     * @param date Date - The published date of the requesting sobi fragment.
     * @return Calendar
     */
    protected Calendar getOrCreateCalendar(CalendarId calendarId, Date date) {
        Calendar calendar;
        try {
            calendar = calendarDataService.getCalendar(calendarId);
        }
        catch (CalendarNotFoundEx ex) {
            calendar = new Calendar(calendarId);
            calendar.setModifiedDate(date);
            calendar.setPublishDate(date);
        }
        return calendar;
    }
}