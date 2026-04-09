export interface Author {
  old_id?: string;
  id: string;
  nameIdentifier?: string;
  full: string;
  family: string;
  given: string;

  date: string;
  date_birth?: string;
  date_death?: string;
  year_birth?: number;
  year_death?: number;
  confirmed_start_year?: boolean;
  confirmed_end_year?: boolean;

  note?: string;
  labels?: string[];
}

export interface AuthorTitle extends Author {
  removed?: boolean;
  role?: string[];
  isLast?: boolean;
}


