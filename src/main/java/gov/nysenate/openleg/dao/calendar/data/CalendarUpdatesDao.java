package gov.nysenate.openleg.dao.calendar.data;

import com.google.common.collect.Range;
import gov.nysenate.openleg.dao.base.LimitOffset;
import gov.nysenate.openleg.dao.base.PaginatedList;
import gov.nysenate.openleg.dao.base.SortOrder;
import gov.nysenate.openleg.model.calendar.CalendarId;
import gov.nysenate.openleg.model.updates.UpdateDigest;
import gov.nysenate.openleg.model.updates.UpdateToken;
import gov.nysenate.openleg.model.updates.UpdateTokenDigest;

import java.time.LocalDateTime;
import java.util.List;

public interface CalendarUpdatesDao {

    /**
     * Returns a list of ids for calendars that have been updated within the specified date time range
     *
     * @param dateTimeRange Range<LocalDateTime> - Date range to search for digests within
     * @param dateOrder SortOrder - Order by the update date/time.
     * @param limitOffset LimitOffset - Restrict the result set
     * @return PaginatedList<CalendarUpdateToken>
     */
    public PaginatedList<UpdateToken<CalendarId>> calendarsUpdatedDuring(Range<LocalDateTime> dateTimeRange,
                                                                         SortOrder dateOrder, LimitOffset limitOffset);

    /**
     * Gets a list of calendar update digests for a given calendar that detail the changes made to that calendar
     *  over the given date time range
     *
     * @param calendarId CalendarId
     * @param dateTimeRange Range<LocalDateTime>
     * @param dateOrder SortOrder
     * @return PaginatedList<CalendarUpdateDigest>
     */
    public List<UpdateDigest<CalendarId>> getUpdateDigests(CalendarId calendarId,
                                                           Range<LocalDateTime> dateTimeRange, SortOrder dateOrder);

    /**
     * Returns a list of calendar ids along with update digests that have been updated within the specified date time range
     *
     * @param dateTimeRange
     * @param dateOrder
     * @param limitOffset
     * @return
     */
    public PaginatedList<UpdateTokenDigest<CalendarId>> getUpdateTokenDigests(Range<LocalDateTime> dateTimeRange,
                                                                              SortOrder dateOrder, LimitOffset limitOffset);

}
