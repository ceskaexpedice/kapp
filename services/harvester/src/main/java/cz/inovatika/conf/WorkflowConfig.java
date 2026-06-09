package cz.inovatika.conf;

import java.time.Year;

public class WorkflowConfig {

    private int publicYear;
    private int onsiteYear;
    private int authorDeadEnoughYear;


    public int getOnsiteYear() {
        return onsiteYear;
    }

    public int getAuthorDeadEnoughYear() {
        return authorDeadEnoughYear;
    }

    public int getPublicYear() {
        return publicYear;
    }

    private int calculateYear(int s) {
        Year y = Year.now();
        y = y.minusYears(s);
        return y.getValue();
    }

    public int calculatePublicYear() {
        return calculateYear(publicYear);
    }

    public int calculateAuthorDeadYear() {
        return calculateYear(authorDeadEnoughYear);
    }

    public int calculateOnsiteYear() {
        return calculateYear(onsiteYear);
    }

    public boolean isPublicYear(int year_published) {
        return year_published <= calculatePublicYear();
    }

    public boolean isOnsiteYear(int year_published) {
        return year_published >= calculateOnsiteYear();
    }
    public boolean isAuthorDeadEnoughYear(int year_died,Integer year_published) {
        if (year_published != null)
            return year_died < calculateAuthorDeadYear() && !isOnsiteYear(year_published);
        return year_died < calculateAuthorDeadYear();
    }
    public boolean isPublishableYear(Integer year_died,Integer year_published) {
        return (year_published!=null && isPublicYear(year_published)) || (year_died !=null && isAuthorDeadEnoughYear(year_died,year_published));
    }

    public Integer calculateReleaseYear(Integer dateIssuedYear, Integer lastAuthorDeathYear) {
        Integer issuedReleaseYear = dateIssuedYear != null ? dateIssuedYear + publicYear : null;
        Integer authorReleaseYear = null;
        if (dateIssuedYear != null && lastAuthorDeathYear != null) {//Everything is known calculate release year based on both
            authorReleaseYear = Math.max(
                    dateIssuedYear + onsiteYear,
                    lastAuthorDeathYear + authorDeadEnoughYear
            );
        } else if (lastAuthorDeathYear != null) {
            authorReleaseYear = lastAuthorDeathYear + authorDeadEnoughYear;//Issued year not known assume authorDeadEnoughYear from death year only
        }

        if (issuedReleaseYear == null) {//Issued year not known assume public year from death year only if possible
            return authorReleaseYear;
        }
        if (authorReleaseYear == null) {//Author death year not known assume public year from issued year only
            return issuedReleaseYear;
        }
        return Math.min(issuedReleaseYear, authorReleaseYear);
    }
}
