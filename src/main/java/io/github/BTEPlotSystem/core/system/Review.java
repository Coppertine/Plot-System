/*
 * The MIT License (MIT)
 *
 *  Copyright © 2021, Alps BTE <bte.atchli@gmail.com>
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package github.BTEPlotSystem.core.system;

import github.BTEPlotSystem.core.database.DatabaseConnection;
import github.BTEPlotSystem.core.system.plot.Plot;
import github.BTEPlotSystem.utils.enums.Category;
import github.BTEPlotSystem.utils.enums.Status;

import java.sql.*;
import java.time.LocalDate;
import java.util.UUID;

public class Review {

    private final int reviewID;

    public Review(int reviewID) {
        this.reviewID = reviewID;
    }

    public Review(int plotID, UUID reviewer, String rating) throws SQLException {
        ResultSet rs = DatabaseConnection.createStatement("SELECT id + 1 available_id FROM plotsystem_reviews t WHERE NOT EXISTS (SELECT * FROM plotsystem_reviews " +
                "WHERE plotsystem_reviews.id = t.id + 1) ORDER BY id LIMIT 1").executeQuery();

        if(rs.next()) {
            this.reviewID = rs.getInt(1);
        } else {
            this.reviewID = 1;
        }

        DatabaseConnection.createStatement("INSERT INTO plotsystem_reviews (id, reviewer_uuid, rating, review_date, feedback) VALUES (?, ?, ?, ?, ?)")
                .setValue(this.reviewID)
                .setValue(reviewer.toString())
                .setValue(rating)
                .setValue(java.sql.Date.valueOf(java.time.LocalDate.now()))
                .setValue("No Feedback")
                .executeUpdate();

        DatabaseConnection.createStatement("UPDATE plotsystem_plots SET review_id = ? WHERE id = ?")
                .setValue(this.reviewID)
                .setValue(plotID)
                .executeUpdate();
    }

    public int getReviewID() {
        return reviewID;
    }

    public int getPlotID() throws SQLException {
        ResultSet rs = DatabaseConnection.createStatement("SELECT id FROM plotsystem_plots WHERE review_id = ?")
                .setValue(this.reviewID).executeQuery();

        if (rs.next()) {
            return rs.getInt(1);
        }
        return 0;
    }

    public Builder getReviewer() throws SQLException {
        ResultSet rs = DatabaseConnection.createStatement("SELECT reviewer_uuid FROM plotsystem_reviews WHERE id = ?")
                .setValue(this.reviewID).executeQuery();

        if (rs.next()) {
            return new Builder(UUID.fromString(rs.getString(1)));
        }
        return null;
    }

    public int getRating(Category category) throws SQLException {
        ResultSet rs = DatabaseConnection.createStatement("SELECT rating FROM plotsystem_reviews WHERE id = ?")
                .setValue(this.reviewID).executeQuery();

        if (rs.next()) {
            String[] scoreAsString = rs.getString("rating").split(",");
            switch (category) {
                case ACCURACY:
                    return Integer.parseInt(scoreAsString[0]);
                case BLOCKPALETTE:
                    return Integer.parseInt(scoreAsString[1]);
                case DETAILING:
                    return Integer.parseInt(scoreAsString[2]);
                case TECHNIQUE:
                    return Integer.parseInt(scoreAsString[3]);
                case ALL:
                    return Integer.parseInt(scoreAsString[0]) + Integer.parseInt(scoreAsString[1]) + Integer.parseInt(scoreAsString[2]) + Integer.parseInt(scoreAsString[3]);
                default:
                    return 0;
            }
        }
        return 0;
    }

    public String getFeedback() throws SQLException {
        ResultSet rs = DatabaseConnection.createStatement("SELECT feedback FROM plotsystem_reviews WHERE id = ?")
                .setValue(this.reviewID).executeQuery();

        if (rs.next()) {
            return rs.getString(1);
        }
        return null;
    }

    public Date getReviewDate() throws SQLException {
        ResultSet rs = DatabaseConnection.createStatement("SELECT review_date FROM plotsystem_reviews WHERE id = ?")
                .setValue(this.reviewID).executeQuery();

        if (rs.next()) {
            return rs.getDate(1);
        }
        return null;
    }

    public void setReviewer(UUID reviewer) throws SQLException {
        DatabaseConnection.createStatement("UPDATE plotsystem_reviews SET reviewer_uuid = ? WHERE id = ?")
                .setValue(reviewer.toString()).setValue(this.reviewID).executeUpdate();
    }

    public void setRating(String ratingFormat) throws SQLException {
        DatabaseConnection.createStatement("UPDATE plotsystem_reviews SET rating = ? WHERE id = ?")
                .setValue(ratingFormat).setValue(this.reviewID).executeUpdate();
    }

    public void setFeedback(String feedback) throws SQLException {
        String[] feedbackArr = feedback.split(" ");
        StringBuilder finalFeedback = new StringBuilder();
        int lineLength = 0;
        int lines = 0;

        for (String word : feedbackArr) {
            if((lineLength + word.length()) <= 60) {
                finalFeedback.append((lines == 0 && lineLength == 0) ? "" : " ").append(word);
                lineLength += word.length();
            } else {
                finalFeedback.append("//").append(word);
                lineLength = 0;
                lines++;
            }
        }

        DatabaseConnection.createStatement("UPDATE plotsystem_reviews SET feedback = ? WHERE id = ?")
                .setValue(finalFeedback.toString()).setValue(this.reviewID).executeUpdate();
    }

    public void setFeedbackSent(boolean isSent) throws SQLException {
        DatabaseConnection.createStatement("UPDATE plotsystem_reviews SET sent = ? WHERE id = ?")
                .setValue(isSent ? 1 : 0).setValue(this.reviewID).executeUpdate();
    }

    public void setReviewDate() throws SQLException {
        DatabaseConnection.createStatement("UPDATE plotsystem_reviews SET review_date = ? WHERE id = ?")
                .setValue(Date.valueOf(LocalDate.now())).setValue(this.reviewID).executeUpdate();
    }

    public boolean isFeedbackSent() throws SQLException {
        ResultSet rs = DatabaseConnection.createStatement("SELECT sent FROM plotsystem_reviews WHERE id = ?")
                .setValue(this.reviewID).executeQuery();

        if (rs.next()) {
            return rs.getInt(1) != 0;
        }
        return false;
    }

    public static void undoReview(Review review) throws SQLException {
        Plot plot = new Plot(review.getPlotID());

        for (Builder member : plot.getPlotMembers()) {
            member.addScore(-plot.getSharedScore());
            member.addCompletedBuild(-1);

            if (member.getFreeSlot() != null) {
                member.setPlot(plot.getID(), member.getFreeSlot());
            }
        }

        plot.getPlotOwner().addScore(-plot.getSharedScore());
        plot.getPlotOwner().addCompletedBuild(-1);
        plot.setScore(-1);
        plot.setStatus(Status.unreviewed);

        if(plot.getPlotOwner().getFreeSlot() != null) {
            plot.getPlotOwner().setPlot(plot.getID(), plot.getPlotOwner().getFreeSlot());
        }

        DatabaseConnection.createStatement("UPDATE plotsystem_plots SET review_id = DEFAULT(review_id) WHERE id = ?")
                .setValue(review.getPlotID()).executeUpdate();

        DatabaseConnection.createStatement("DELETE FROM plotsystem_reviews WHERE id = ?")
                .setValue(review.reviewID).executeUpdate();
    }
}
