package gov.nysenate.openleg.dao.hearing;

import gov.nysenate.openleg.dao.base.LimitOffset;
import gov.nysenate.openleg.dao.base.SortOrder;
import gov.nysenate.openleg.model.hearing.PublicHearing;
import gov.nysenate.openleg.model.hearing.PublicHearingFile;
import gov.nysenate.openleg.model.hearing.PublicHearingId;

import java.util.List;

public interface PublicHearingDao
{

    /**
     * Retrieves all PublicHearingId's.
     * @return
     */
    public List<PublicHearingId> getPublicHearingIds(SortOrder dateOrder, LimitOffset limOff);

    /**
     * Retrieves a {@link PublicHearing} via its {@link PublicHearingId}.
     * @param publicHearingId
     * @return
     */
    public PublicHearing getPublicHearing(PublicHearingId publicHearingId);

    /**
     * Updates the backing store with the given instance or inserts
     * if if the record doesn't already exist.
     * @param publicHearing The {@link gov.nysenate.openleg.model.hearing.PublicHearing} to update.
     * @param publicHearingFile The {@link gov.nysenate.openleg.model.hearing.PublicHearingFile}
     *                          which updated the Public Hearing.
     */
    public void updatePublicHearing(PublicHearing publicHearing, PublicHearingFile publicHearingFile);

}