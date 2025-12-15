import React, {
  createContext,
  useContext,
  useState,
  useCallback,
  ReactNode,
  useEffect,
  useRef,
} from "react";

interface Landmark {
  name: string;
  ref: React.RefObject<HTMLElement>;
}

interface LandmarkRegistrationContextProps {
  registerLandmark: (name: string, ref: React.RefObject<HTMLElement>) => void;
  unregisterLandmark: (name: string) => void;
}

interface LandmarksListContextProps {
  landmarks: Landmark[];
}

const LandmarkRegistrationContext = createContext<
  LandmarkRegistrationContextProps | undefined
>(undefined);
const LandmarksListContext = createContext<
  LandmarksListContextProps | undefined
>(undefined);

export const LandmarksProvider: React.FC<{ children: ReactNode }> = ({
  children,
}) => {
  const [landmarks, setLandmarks] = useState<Landmark[]>([]);

  const registerLandmark = useCallback(
    (name: string, ref: React.RefObject<HTMLElement>) => {
      setLandmarks((prev) => {
        const existingIndex = prev.findIndex(
          (landmark) => landmark.name === name,
        );
        if (existingIndex >= 0) {
          const updated = [...prev];
          updated[existingIndex] = { name, ref };
          return updated;
        }
        return [...prev, { name, ref }];
      });
    },
    [],
  );

  const unregisterLandmark = useCallback((name: string) => {
    setLandmarks((prev) => prev.filter((landmark) => landmark.name !== name));
  }, []);

  return (
    <LandmarkRegistrationContext.Provider
      value={{ registerLandmark, unregisterLandmark }}
    >
      <LandmarksListContext.Provider value={{ landmarks }}>
        {children}
      </LandmarksListContext.Provider>
    </LandmarkRegistrationContext.Provider>
  );
};

export const useLandmarkRegistration = () => {
  const context = useContext(LandmarkRegistrationContext);
  if (!context) {
    throw new Error(
      "useLandmarkRegistration must be used within a LandmarksProvider",
    );
  }
  return context;
};

export const useLandmarksList = () => {
  const context = useContext(LandmarksListContext);
  if (!context) {
    throw new Error("useLandmarksList must be used within a LandmarksProvider");
  }
  return context;
};

export const useLandmark = (name: string) => {
  const ref = useRef<HTMLElement>(null);
  const { registerLandmark, unregisterLandmark } = useLandmarkRegistration();

  useEffect(() => {
    registerLandmark(name, ref);
    return () => {
      unregisterLandmark(name);
    };
  }, [name, registerLandmark, unregisterLandmark]);

  return ref;
};
